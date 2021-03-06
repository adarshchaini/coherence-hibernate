/*
 * File: HibernateCacheStore.java
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * The contents of this file are subject to the terms and conditions of
 * the Common Development and Distribution License 1.0 (the "License").
 *
 * You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the License by consulting the LICENSE.txt file
 * distributed with this file, or by consulting https://oss.oracle.com/licenses/CDDL
 *
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file LICENSE.txt.
 *
 * MODIFICATIONS:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 */

package com.oracle.coherence.hibernate.cachestore;


import com.tangosol.net.cache.CacheStore;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.engine.spi.SessionImplementor;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;


/**
 * Data-driven CacheStore implementation for Hibernate tables
 *
 * @author jp 2005.09.15
 * @author pp 2009.01.23
 * @author rs 2013.09.05
 */
public class HibernateCacheStore
        extends HibernateCacheLoader
        implements CacheStore
{
    // ----- Constructor(s) -------------------------------------------------

    /**
     * Default constructor.  If using this constructor, it is expected that
     * the <tt>entityName</tt> and <tt>sessionFactory</tt> attributes will
     * be set prior to usage.
     */
    public HibernateCacheStore()
    {
        super();
    }

    /**
     * Constructor which accepts an entityName
     *
     * @param entityName    the Hibernate entity (the fully-qualified class name)
     */
    public HibernateCacheStore(String entityName)
    {
        super(entityName);
    }

    /**
     * Constructor which accepts an entityName and a hibernate configuration
     * resource. The current implementation instantiates a SessionFactory per
     * instance (implying one instance per CacheStore-backed NamedCache).
     *
     * @param sEntityName Hibernate entity (i.e. the HQL table name)
     * @param sResource   Hibernate config classpath resource (e.g.
     *                     hibernate.cfg.xml)
     */
    public HibernateCacheStore(String sEntityName, String sResource)
    {
        super(sEntityName, sResource);
    }

    /**
     * Constructor which accepts an entityName and a hibernate configuration
     * resource. The current implementation instantiates a SessionFactory per
     * instance (implying one instance per CacheStore-backed NamedCache).
     *
     * @param sEntityName        Hibernate entity (i.e. the HQL table name)
     * @param configurationFile  Hibernate config file (e.g. hibernate.cfg.xml)
     */
    public HibernateCacheStore(String sEntityName, File configurationFile)
    {
        super(sEntityName, configurationFile);
    }

    /**
     * Constructor which accepts an entityName and a Hibernate
     * <tt>SessionFactory</tt>.  This allows for external configuration
     * of the SessionFactory (for instance using Spring.)
     *
     * @param sEntityName       Hibernate entity (i.e. the HQL table name)
     * @param sessionFactory    Hibernate SessionFactory
     */
    public HibernateCacheStore(String sEntityName, SessionFactory sessionFactory)
    {
        super(sEntityName, sessionFactory);
    }


    // ----- CacheStore API methods -----------------------------------------

    /**
     * Store a Hibernate entity given an id (key) and entity (value)
     * <p/>
     * The entity must have an identifier attribute, and it must be either
     * null (undefined) or equal to the cache key.
     *
     * @param key   the cache key; specifically, the entity id
     * @param value the cache value; specifically, the entity
     */
    public void store(Object key, Object value)
    {
        ensureInitialized();

        Transaction tx = null;

        Session session = openSession();

        try
        {
            tx = session.beginTransaction();

            validateIdentifier((Serializable)key, value, (SessionImplementor) session);

            // Save or Update (since we don't know if this is an insert or an
            // update)
            session.merge(getEntityName(), value);

            tx.commit();
        }
        catch (Exception e)
        {
            if (tx != null)
            {
                tx.rollback();
            }

            throw ensureRuntimeException(e);
        }
        finally
        {
            closeSession(session);
        }
    }

    /**
     * Store a collection of Hibernate entities given a Map of ids (keys) and
     * entities (values)
     *
     * @param entries   a mapping of ids (keys) to entities (values)
     */
    public void storeAll(Map entries)
    {
        ensureInitialized();

        Transaction tx = null;

        Session session = openSession();

        try
        {
            tx = session.beginTransaction();

            // We just iterate through the incoming set and individually
            // save each one. Note that this is still part of a single
            // Hibernate transaction so it may batch them.
            for (Iterator iter = entries.entrySet().iterator(); iter.hasNext(); )
            {
                Map.Entry entry = (Map.Entry)iter.next();
                Serializable id = (Serializable)entry.getKey();
                Object entity = entry.getValue();
                validateIdentifier(id, entity, (SessionImplementor) session);
                session.merge(entity);
            }

            tx.commit();
        }
        catch (Exception e)
        {
            if (tx != null)
            {
                tx.rollback();
            }

            throw ensureRuntimeException(e);
        }
        finally
        {
            closeSession(session);
        }
    }

    /**
     * Erase a Hibernate entity given an id (key)
     *
     * @param key   the cache key; specifically, the entity id
     */
    public void erase(Object key)
    {
        ensureInitialized();

        Transaction tx = null;

        Session session = openSession();

        try
        {
            tx = session.beginTransaction();

            // Hibernate deletes objects ... it has no "delete by key".
            // So we need to load the objects before we delete them.
            // We may be able to use an HQL delete instead.
            Object entity = createEntityFromId(key, (SessionImplementor) session);
            if (entity != null)
            {
                session.delete(entity);
            }

            tx.commit();
        }
        catch (Exception e)
        {
            if (tx != null)
            {
                tx.rollback();
            }

            throw ensureRuntimeException(e);
        }
        finally
        {
            closeSession(session);
        }
    }

    /**
     * Erase a set of Hibernate entities given an collection of ids (keys)
     *
     * @param keys  the cache keys; specifically, the entity ids
     */
    public void eraseAll(Collection keys)
    {
        ensureInitialized();

        Transaction tx = null;

        Session session = openSession();

        try
        {
            tx = session.beginTransaction();

            // We just iterate through the incoming set and individually
            // delete each one. Note that this is still part of a single
            // Hibernate transaction so it may batch them.
            for (Iterator iter = keys.iterator(); iter.hasNext();)
            {
                Object key = iter.next();
                Object entity = createEntityFromId(key, (SessionImplementor) session);
                if (entity != null)
                {
                    session.delete(entity);
                }
            }

            tx.commit();
        }
        catch (Exception e)
        {
            if (tx != null)
            {
                tx.rollback();
            }

            throw ensureRuntimeException(e);
        }
        finally
        {
            closeSession(session);
        }
    }
}
