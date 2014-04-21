package org.sirix.xquery.node;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnegative;
import javax.xml.stream.XMLEventReader;

import org.brackit.xquery.node.AbstractCollection;
import org.brackit.xquery.node.parser.CollectionParser;
import org.brackit.xquery.node.parser.SubtreeHandler;
import org.brackit.xquery.node.parser.SubtreeListener;
import org.brackit.xquery.node.parser.SubtreeParser;
import org.brackit.xquery.node.stream.ArrayStream;
import org.brackit.xquery.xdm.AbstractTemporalNode;
import org.brackit.xquery.xdm.DocumentException;
import org.brackit.xquery.xdm.OperationNotSupportedException;
import org.brackit.xquery.xdm.Stream;
import org.brackit.xquery.xdm.TemporalCollection;
import org.sirix.access.Databases;
import org.sirix.access.conf.DatabaseConfiguration;
import org.sirix.access.conf.ResourceConfiguration;
import org.sirix.access.conf.SessionConfiguration;
import org.sirix.api.Database;
import org.sirix.api.NodeReadTrx;
import org.sirix.api.NodeWriteTrx;
import org.sirix.api.Session;
import org.sirix.exception.SirixException;
import org.sirix.exception.SirixIOException;
import org.sirix.service.xml.shredder.Insert;
import org.sirix.utils.LogWrapper;
import org.slf4j.LoggerFactory;

/**
 * Database collection.
 * 
 * @author Johannes Lichtenberger
 * 
 */
public final class DBCollection extends
		AbstractCollection<AbstractTemporalNode<DBNode>> implements
		TemporalCollection<AbstractTemporalNode<DBNode>>, AutoCloseable {

	/** Logger. */
	private static final LogWrapper LOGGER = new LogWrapper(
			LoggerFactory.getLogger(DBCollection.class));

	/** ID sequence. */
	private static final AtomicInteger ID_SEQUENCE = new AtomicInteger();

	/** {@link Sirix} database. */
	private final Database mDatabase;

	/** Unique ID. */
	private final int mID;
	
	private static final ConcurrentMap<SessionConfiguration, Long> mUncommitedWtxPerResource = new ConcurrentHashMap<>();

	/**
	 * Constructor.
	 * 
	 * @param name
	 *          collection name
	 * @param database
	 *          Sirix {@link Database} reference
	 */
	public DBCollection(final String name, final Database database) {
		super(checkNotNull(name));
		mDatabase = checkNotNull(database);
		mID = ID_SEQUENCE.incrementAndGet();
	}

	/**
	 * Get the unique ID.
	 * 
	 * @return unique ID
	 */
	public int getID() {
		return mID;
	}

	/**
	 * Get the underlying Sirix {@link Database}.
	 * 
	 * @return Sirix {@link Database}
	 */
	public Database getDatabase() {
		return mDatabase;
	}

	@Override
	public void delete() throws DocumentException {
		try {
			Databases.truncateDatabase(new DatabaseConfiguration(mDatabase
					.getDatabaseConfig().getFile()));
		} catch (final SirixIOException e) {
			throw new DocumentException(e.getCause());
		}
	}

	@Override
	public void remove(final long documentID)
			throws OperationNotSupportedException, DocumentException {
		if (documentID >= 0) {
			final String resource = mDatabase.getResourceName((int) documentID);
			if (resource != null) {
				mDatabase.truncateResource(resource);
			}
		}
	}

	@Override
	public DBNode getDocument(final @Nonnegative int revision)
			throws DocumentException {
		final String[] resources = mDatabase.listResources();
		if (resources.length > 1) {
			throw new DocumentException("More than one document stored in database/collection!");
		}
		try {
			final Session session = mDatabase.getSession(SessionConfiguration
					.newBuilder(resources[0]).build());
			final int version = revision == -1 ? session
					.getMostRecentRevisionNumber() : revision;
			final NodeReadTrx rtx = session.beginNodeReadTrx(version);
			return new DBNode(rtx, this);
		} catch (final SirixException e) {
			throw new DocumentException(e.getCause());
		}
	}

	public DBNode add(final String resName, SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException {
		try {
			final String resource = new StringBuilder(2).append("resource")
					.append(mDatabase.listResources().length + 1).toString();
			mDatabase.createResource(ResourceConfiguration
					.newBuilder(resource, mDatabase.getDatabaseConfig())
					.useDeweyIDs(true).useTextCompression(true).buildPathSummary(true)
					.build());
			final Session session = mDatabase.getSession(SessionConfiguration
					.newBuilder(resource).build());
			final NodeWriteTrx wtx = session.beginNodeWriteTrx();

			final SubtreeHandler handler = new SubtreeBuilder(
					this,
					wtx,
					Insert.ASFIRSTCHILD,
					Collections
							.<SubtreeListener<? super AbstractTemporalNode<DBNode>>> emptyList());

			// Make sure the CollectionParser is used.
			if (!(parser instanceof CollectionParser)) {
				parser = new CollectionParser(parser);
			}

			parser.parse(handler);
			return new DBNode(wtx, this);
		} catch (final SirixException e) {
			LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	@Override
	public DBNode add(SubtreeParser parser)
			throws OperationNotSupportedException, DocumentException {
		try {
			final String resource = new StringBuilder(2).append("resource")
					.append(mDatabase.listResources().length + 1).toString();
			mDatabase.createResource(ResourceConfiguration
					.newBuilder(resource, mDatabase.getDatabaseConfig())
					.useDeweyIDs(true).useTextCompression(true).buildPathSummary(true)
					.build());
			final Session session = mDatabase.getSession(SessionConfiguration
					.newBuilder(resource).build());
			final NodeWriteTrx wtx = session.beginNodeWriteTrx();

			final SubtreeHandler handler = new SubtreeBuilder(
					this,
					wtx,
					Insert.ASFIRSTCHILD,
					Collections
							.<SubtreeListener<? super AbstractTemporalNode<DBNode>>> emptyList());

			// Make sure the CollectionParser is used.
			if (!(parser instanceof CollectionParser)) {
				parser = new CollectionParser(parser);
			}

			parser.parse(handler);
			return new DBNode(wtx, this);
		} catch (final SirixException e) {
			LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	public DBNode add(final String resource, final XMLEventReader reader)
			throws OperationNotSupportedException, DocumentException {
		try {
			mDatabase.createResource(ResourceConfiguration
					.newBuilder(resource, mDatabase.getDatabaseConfig())
					.useDeweyIDs(true).build());
			final Session session = mDatabase.getSession(SessionConfiguration
					.newBuilder(resource).build());
			final NodeWriteTrx wtx = session.beginNodeWriteTrx();
			wtx.insertSubtreeAsFirstChild(reader);
			wtx.moveToDocumentRoot();
			return new DBNode(wtx, this);
		} catch (final SirixException e) {
			LOGGER.error(e.getMessage(), e);
			return null;
		}
	}

	@Override
	public void close() throws SirixException {
		mDatabase.close();
	}

	@Override
	public long getDocumentCount() {
		return mDatabase.listResources().length;
	}

	@Override
	public DBNode getDocument() throws DocumentException {
		return getDocument(-1);
	}

	@Override
	public Stream<DBNode> getDocuments() throws DocumentException {
		return getDocuments(false);
	}

	@Override
	public DBNode getDocument(final int revision, final String name) throws DocumentException {
		return getDocument(revision, name, false);
	}

	@Override
	public DBNode getDocument(final String name) throws DocumentException {
		return getDocument(-1, name, false);
	}

	@Override
	public DBNode getDocument(final int revision, final String name,
			final boolean updatable) throws DocumentException {
		try {
			final SessionConfiguration sessionConfig = SessionConfiguration
					.newBuilder(name).build();
			final Session session = mDatabase.getSession(sessionConfig);
			final int version = revision == -1 ? session
					.getMostRecentRevisionNumber() : revision;
					
			final NodeReadTrx rtx;
			if (updatable) {
				if (session.getAvailableNodeWriteTrx() == 0) {
					final Optional<NodeWriteTrx> optionalWriteTrx;
					optionalWriteTrx = session.getNodeWriteTrx(mUncommitedWtxPerResource.get(sessionConfig));
					
					// Must exist.
					rtx = optionalWriteTrx.get();					
				}
				else
					rtx = session.beginNodeWriteTrx();
				mUncommitedWtxPerResource.put(sessionConfig, rtx.getTransactionID());
				
				if (version < session.getMostRecentRevisionNumber())
					((NodeWriteTrx) rtx).revertTo(version);
			} else {
				rtx = session.beginNodeReadTrx(version);
			}
				
			return new DBNode(rtx, this);
		} catch (final SirixException e) {
			throw new DocumentException(e.getCause());
		}
	}

	@Override
	public DBNode getDocument(final int revision, final boolean updatable)
			throws DocumentException {
		final String[] resources = mDatabase.listResources();
		if (resources.length > 1) {
			throw new DocumentException("More than one document stored in database/collection!");
		}
		try {
			final Session session = mDatabase.getSession(SessionConfiguration
					.newBuilder(resources[0]).build());
			final int version = revision == -1 ? session
					.getMostRecentRevisionNumber() : revision;
			final NodeReadTrx rtx = updatable ? session.beginNodeWriteTrx() : session.beginNodeReadTrx(version);
			if (updatable	&& version < session.getMostRecentRevisionNumber()) {
				((NodeWriteTrx) rtx).revertTo(version);
			}
			return new DBNode(rtx, this);
		} catch (final SirixException e) {
			throw new DocumentException(e.getCause());
		}
	}

	@Override
	public Stream<DBNode> getDocuments(
			final boolean updatable) throws DocumentException {
		final String[] resources = mDatabase.listResources();
		final List<DBNode> documents = new ArrayList<>(resources.length);
		for (final String resource : resources) {
			try {
				final Session session = mDatabase.getSession(SessionConfiguration
						.newBuilder(resource).build());
				final NodeReadTrx rtx = updatable ? session.beginNodeWriteTrx() : session.beginNodeReadTrx();
				documents.add(new DBNode(rtx, this));
			} catch (final SirixException e) {
				throw new DocumentException(e.getCause());
			}
		}
		return new ArrayStream<DBNode>(documents.toArray(new DBNode[documents
				.size()]));
	}

	@Override
	public DBNode getDocument(boolean updatabale)
			throws DocumentException {
		return getDocument(-1, updatabale);
	}
}
