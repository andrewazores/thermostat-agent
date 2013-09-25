/*
 * Copyright 2012, 2013 Red Hat, Inc.
 *
 * This file is part of Thermostat.
 *
 * Thermostat is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2, or (at your
 * option) any later version.
 *
 * Thermostat is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Thermostat; see the file COPYING.  If not see
 * <http://www.gnu.org/licenses/>.
 *
 * Linking this code with other modules is making a combined work
 * based on this code.  Thus, the terms and conditions of the GNU
 * General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this code give
 * you permission to link this code with independent modules to
 * produce an executable, regardless of the license terms of these
 * independent modules, and to copy and distribute the resulting
 * executable under terms of your choice, provided that you also
 * meet, for each linked independent module, the terms and conditions
 * of the license of that module.  An independent module is a module
 * which is not derived from or based on this code.  If you modify
 * this code, you may extend this exception to your version of the
 * library, but you are not obligated to do so.  If you do not wish
 * to do so, delete this exception statement from your version.
 */

package com.redhat.thermostat.storage.mongodb.internal;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import com.redhat.thermostat.storage.config.StartupConfiguration;
import com.redhat.thermostat.storage.core.AbstractQuery.Sort;
import com.redhat.thermostat.storage.core.Add;
import com.redhat.thermostat.storage.core.AggregateQuery;
import com.redhat.thermostat.storage.core.AggregateQuery.AggregateFunction;
import com.redhat.thermostat.storage.core.BackingStorage;
import com.redhat.thermostat.storage.core.AddReplaceHelper;
import com.redhat.thermostat.storage.core.Category;
import com.redhat.thermostat.storage.core.Connection;
import com.redhat.thermostat.storage.core.Connection.ConnectionListener;
import com.redhat.thermostat.storage.core.Connection.ConnectionStatus;
import com.redhat.thermostat.storage.core.Cursor;
import com.redhat.thermostat.storage.core.DescriptorParsingException;
import com.redhat.thermostat.storage.core.Key;
import com.redhat.thermostat.storage.core.PreparedStatement;
import com.redhat.thermostat.storage.core.PreparedStatementFactory;
import com.redhat.thermostat.storage.core.Query;
import com.redhat.thermostat.storage.core.Remove;
import com.redhat.thermostat.storage.core.Replace;
import com.redhat.thermostat.storage.core.StatementDescriptor;
import com.redhat.thermostat.storage.core.Update;
import com.redhat.thermostat.storage.model.AggregateCount;
import com.redhat.thermostat.storage.model.AggregateResult;
import com.redhat.thermostat.storage.model.Pojo;
import com.redhat.thermostat.storage.query.Expression;

/**
 * Implementation of the Storage interface that uses MongoDB to store the instrumentation data.
 *
 * In this implementation, each CATEGORY is given a distinct collection.
 */
public class MongoStorage implements BackingStorage {
    
    private class MongoCountQuery<T extends Pojo> extends AggregateQuery<T> {
        
        private final Category<T> category;
        
        private MongoCountQuery(MongoQuery<T> queryToAggregate, Category<T> category) {
            super(AggregateFunction.COUNT, queryToAggregate);
            this.category = category;
        }

        @Override
        public Cursor<T> execute() {
            return executeGetCount(category, (MongoQuery<T>)this.queryToAggregate);
        }
    }

    private class MongoAdd<T extends Pojo> extends AddReplaceHelper implements Add<T> {

        private MongoAdd(Category<?> category) {
            super(category);
        }
        
        @Override
        public int apply() {
            return addImpl(getCategory(), getPojo());
        }
        
    }

    private class MongoReplace<T extends Pojo> extends AddReplaceHelper implements Replace<T> {
        
        private DBObject query;
        private final MongoExpressionParser parser;

        private MongoReplace(Category<?> category) {
            super(category);
            this.parser = new MongoExpressionParser();
        }
        
        @Override
        public int apply() {
            if (query == null) {
                String msg = "where expression must be set. " +
                             "Please call where() before apply().";
                throw new IllegalStateException(msg);
            }
            return replaceImpl(getCategory(), getPojo(), query);
        }

        @Override
        public void where(Expression expression) {
            this.query = parser.parse(Objects.requireNonNull(expression));
        }
        
    }
    
    private class MongoRemove<T extends Pojo> implements Remove<T> {

        @SuppressWarnings("rawtypes")
        private final Category category;
        private DBObject query;
        private final MongoExpressionParser parser;
        
        private MongoRemove(Category<?> category) {
            this(category, new MongoExpressionParser());
        }
        
        private MongoRemove(Category<?> category, MongoExpressionParser parser) {
            this.parser = parser;
            this.category = category;
        }

        @Override
        public void where(Expression expr) {
            query = parser.parse(expr);
        }
        
        @Override
        public int apply() {
            return removePojo(category, query);
        }
        
    }

    private final MongoConnection conn;
    private final Map<String, DBCollection> collectionCache = new HashMap<String, DBCollection>();
    private final CountDownLatch connectedLatch;
    private DB db = null;

    // For testing only
    MongoStorage(DB db, CountDownLatch latch) {
        this.db = db;
        this.connectedLatch = latch;
        this.conn = null;
    }
    
    public MongoStorage(StartupConfiguration conf) {
        conn = new MongoConnection(conf);
        connectedLatch = new CountDownLatch(1);
        conn.addListener(new ConnectionListener() {
            @Override
            public void changed(ConnectionStatus newStatus) {
                switch (newStatus) {
                case DISCONNECTED:
                    db = null;
                case CONNECTED:
                    db = conn.getDB();
                    // This is important. See comment in registerCategory().
                    connectedLatch.countDown();
                default:
                    // ignore other status events
                }
            }
        });
    }

    public <T extends Pojo> Cursor<T> executeGetCount(Category<T> category, MongoQuery<T> queryToAggregate) {
        DBCollection coll = getCachedCollection(category);
        long count = 0L;
        DBObject query = queryToAggregate.getGeneratedQuery();
        if (coll != null) {
            count = coll.getCount(query);
        }
        AggregateCount result = new AggregateCount();
        result.setCount(count);
        return result.getCursor();
    }

    @Override
    public Connection getConnection() {
        return conn;
    }

    @Override
    public <T extends Pojo> Add<T> createAdd(Category<T> into) {
        MongoAdd<T> add = new MongoAdd<>(into);
        return add;
    }

    @Override
    public <T extends Pojo> Replace<T> createReplace(Category<T> into) {
        MongoReplace<T> replace = new MongoReplace<>(into);
        return replace;
    }

    private int addImpl(final Category<?> cat, final Pojo pojo) {
        DBCollection coll = getCachedCollection(cat);
        DBObject toInsert = preparePut(pojo);
        WriteResult result = coll.insert(toInsert);
        return numAffectedRecords(result);
    }

    private int replaceImpl(final Category<?> cat, final Pojo pojo, final DBObject query) {
        DBCollection coll = getCachedCollection(cat);
        DBObject toInsert = preparePut(pojo);
        WriteResult result = coll.update(query, toInsert, true, false);
        return numAffectedRecords(result);
    }
    
    private int numAffectedRecords(WriteResult result) {
        // response code corresponds to the number of records affected.
        int responseCode = result.getN();
        return responseCode;
    }

    private DBObject preparePut(final Pojo pojo) {
        MongoPojoConverter converter = new MongoPojoConverter();
        DBObject toInsert = converter.convertPojoToMongo(pojo);
        if (toInsert.get(Key.AGENT_ID.getName()) == null) {
            // FIXME: Remove
            throw new AssertionError("agentID must be set");
        }
        return toInsert;
    }

    int updatePojo(MongoUpdate<?> mongoUpdate) {
        Category<?> cat = mongoUpdate.getCategory();
        DBCollection coll = getCachedCollection(cat);
        DBObject query = mongoUpdate.getQuery();
        DBObject values = mongoUpdate.getValues();
        WriteResult result = coll.update(query, values);
        return numAffectedRecords(result);
    }

    private int removePojo(Category<?> category, DBObject query) {
        DBCollection coll = getCachedCollection(category);
        WriteResult result = coll.remove(query);
        return numAffectedRecords(result);
    }

    private DBCollection getCachedCollection(Category<?> category) {
        String collName = category.getName();
        DBCollection coll = collectionCache.get(collName);
        if (coll == null && db.collectionExists(collName)) {
            throw new IllegalStateException("Categories need to be registered before being used");
        }
        return coll;
    }

    // TODO: This method is only temporary to enable tests, until we come up with a better design,
    // in particular, the collection should be stored in the category itself. It must not be called
    // from production code.
    void mapCategoryToDBCollection(Category<?> category, DBCollection coll) {
        collectionCache.put(category.getName(), coll);
    }


    @Override
    public void purge(String agentId) {
        BasicDBObject query = new BasicDBObject(Key.AGENT_ID.getName(), agentId);
        for (String collectionName : db.getCollectionNames()) {
            DBCollection coll = db.getCollectionFromString(collectionName);
            coll.remove(query);
        }
    }
    
    @Override
    public void registerCategory(Category<?> category) {
        Class<?> dataClass = category.getDataClass();
        if (AggregateResult.class.isAssignableFrom(dataClass)) {
            // adapted aggregate category, no need to actually register
            return;
        }
        String name = category.getName();
        if (collectionCache.containsKey(name)) {
            throw new IllegalStateException("Category may only be associated with one backend.");
        }

        // The db field is only set once we've got a connection
        // established. Wait until we actually get notification
        // this has happened. Without this sychronization we might
        // get NPEs since the connection handshake might still be
        // ongoing.
        try {
            connectedLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        DBCollection coll;
        if (! db.collectionExists(name)) {
            coll = db.createCollection(name, new BasicDBObject("capped", false));
        } else {
            coll = db.getCollection(name);
        }
        collectionCache.put(name, coll);
    }

    @Override
    public <T extends Pojo> Query<T> createQuery(Category<T> category) {
        return new MongoQuery<T>(this, category);
    }

    @Override
    public <T extends Pojo> Update<T> createUpdate(Category<T> category) {
        return new MongoUpdate<>(this, category);
    }

    @Override
    public <T extends Pojo> Remove<T> createRemove(Category<T> category) {
        return new MongoRemove<>(category);
    }

    <T extends Pojo> Cursor<T> findAllPojos(MongoQuery<T> mongoQuery, Class<T> resultClass) {
        DBCollection coll = getCachedCollection(mongoQuery.getCategory());
        DBCursor dbCursor;
        if (mongoQuery.hasClauses()) {
            dbCursor = coll.find(mongoQuery.getGeneratedQuery());
        } else {
            dbCursor = coll.find();
        }
        dbCursor = applySortAndLimit(mongoQuery, dbCursor);
        return new MongoCursor<T>(dbCursor, resultClass);
    }

    private DBCursor applySortAndLimit(MongoQuery<?> query, DBCursor dbCursor) {
        BasicDBObject orderBy = new BasicDBObject();
        List<Sort> sorts = query.getSorts();
        for (Sort sort : sorts) {
            orderBy.append(sort.getKey().getName(), sort.getDirection().getValue());
        }
        dbCursor.sort(orderBy);
        int limit = query.getLimit();
        if (limit > 0) {
            dbCursor.limit(limit);
        }
        return dbCursor;
    }

    @Override
    public void saveFile(String filename, InputStream data) {
        GridFS gridFS = new GridFS(db);
        GridFSInputFile inputFile = gridFS.createFile(data, filename);
        inputFile.save();
    }

    @Override
    public InputStream loadFile(String filename) {
        GridFS gridFS = new GridFS(db);
        GridFSDBFile file = gridFS.findOne(filename);
        if (file == null) {
            return null;
        } else {
            return file.getInputStream();
        }
    }

    @Override
    public void shutdown() {
        try {
            // Clean up any pending connections. mongo-java-driver issue 130
            // suggests that Mongo.close() helps with this ThreadLocal business
            // tomcat warns about. See also:
            // IcedTea BZ#1315 and https://jira.mongodb.org/browse/JAVA-130
            db.getMongo().close();
        } catch (Exception e) {
            // ignored
        }
    }

    @Override
    public <T extends Pojo> PreparedStatement<T> prepareStatement(StatementDescriptor<T> statementDesc)
            throws DescriptorParsingException {
        // Queued storage decorator should override this. This should never
        // be called.
        throw new IllegalStateException();
    }

    @Override
    public <T extends Pojo> Query<T> createAggregateQuery(
            AggregateFunction function, Category<T> category) {
        switch (function) {
        case COUNT:
            MongoQuery<T> query = (MongoQuery<T>)createQuery(category);
            return new MongoCountQuery<>(query, category);
        default:
            throw new IllegalStateException("function not supported: "
                    + function);
        }
    }

}

