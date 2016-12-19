/*
 * Copyright 2014 - 2016 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.persistence.impl.hibernate.function;

import com.blazebit.apt.service.ServiceProvider;
import com.blazebit.persistence.impl.hibernate.HibernateJpa21Provider;
import com.blazebit.persistence.impl.hibernate.HibernateJpaProvider;
import com.blazebit.persistence.impl.hibernate.spi.HibernateVersionProvider;
import com.blazebit.persistence.spi.EntityManagerFactoryIntegrator;
import com.blazebit.persistence.spi.JpaProvider;
import com.blazebit.persistence.spi.JpaProviderFactory;
import com.blazebit.persistence.spi.JpqlFunction;
import com.blazebit.persistence.spi.JpqlFunctionGroup;
import org.hibernate.Session;
import org.hibernate.Version;
import org.hibernate.dialect.CUBRIDDialect;
import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.HSQLDialect;
import org.hibernate.dialect.InformixDialect;
import org.hibernate.dialect.IngresDialect;
import org.hibernate.dialect.InterbaseDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.Oracle8iDialect;
import org.hibernate.dialect.Oracle9Dialect;
import org.hibernate.dialect.PostgreSQL81Dialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.SybaseDialect;
import org.hibernate.dialect.function.SQLFunction;
import org.hibernate.dialect.function.SQLFunctionRegistry;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.collection.CollectionPersister;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 *
 * @author Christian Beikov
 * @since 1.0
 */
@SuppressWarnings("deprecation")
@ServiceProvider(EntityManagerFactoryIntegrator.class)
public class HibernateEntityManagerFactoryIntegrator implements EntityManagerFactoryIntegrator {
    
    private static final Logger LOG = Logger.getLogger(EntityManagerFactoryIntegrator.class.getName());

    private static final String VERSION_STRING;
    private static final int MAJOR;
    private static final int MINOR;
    private static final int FIX;
    private static final String TYPE;

    static {
        Iterator<HibernateVersionProvider> iter = ServiceLoader.load(HibernateVersionProvider.class).iterator();
        if (iter.hasNext()) {
            VERSION_STRING = iter.next().getVersion();
        } else {
            VERSION_STRING = Version.getVersionString();
        }
        String[] parts = VERSION_STRING.split("[\\.-]");
        MAJOR = Integer.parseInt(parts[0]);
        MINOR = Integer.parseInt(parts[1]);
        FIX = Integer.parseInt(parts[2]);
        TYPE = parts[3];
    }
    
    @Override
    public String getDbms(EntityManagerFactory entityManagerFactory) {
        if (entityManagerFactory == null) {
            return null;
        }

        EntityManager em = null;
        
        try {
            em = entityManagerFactory.createEntityManager();
            Session s = em.unwrap(Session.class);
            Dialect dialect = getDialect(s);
            return getDbmsName(dialect);
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    private Map<String, CollectionPersister> getCollectionPersisters(EntityManager em) {
        if (em == null) {
            return null;
        }

        return em.unwrap(SessionImplementor.class).getFactory().getCollectionPersisters();
    }

    @Override
    public JpaProviderFactory getJpaProviderFactory(final EntityManagerFactory entityManagerFactory) {
        if (MAJOR > 4 || MAJOR == 4 && MINOR >= 3) {
            return new JpaProviderFactory() {
                @Override
                public JpaProvider createJpaProvider(EntityManager em) {
                    return new HibernateJpa21Provider(em, getDbms(entityManagerFactory), getCollectionPersisters(em), MAJOR, MINOR, FIX);
                }
            };
        } else {
            return new JpaProviderFactory() {
                @Override
                public JpaProvider createJpaProvider(EntityManager em) {
                    return new HibernateJpaProvider(em, getDbms(entityManagerFactory), getCollectionPersisters(em));
                }
            };
        }
    }

    private String getDbmsName(Dialect dialect) {
        if (dialect instanceof MySQLDialect) {
            return "mysql";
        } else if (dialect instanceof DB2Dialect) {
            return "db2";
        } else if (dialect instanceof PostgreSQL81Dialect) {
            return "postgresql";
        } else if (dialect instanceof Oracle8iDialect || dialect instanceof Oracle9Dialect) {
            return "oracle";
        } else if (dialect instanceof SQLServerDialect) {
            return "microsoft";
        } else if (dialect instanceof SybaseDialect) {
            return "sybase";
        } else if (dialect instanceof H2Dialect) {
            return "h2";
        } else if (dialect instanceof CUBRIDDialect) {
            return "cubrid";
        } else if (dialect instanceof HSQLDialect) {
            return "hsql";
        } else if (dialect instanceof InformixDialect) {
            return "informix";
        } else if (dialect instanceof IngresDialect) {
            return "ingres";
        } else if (dialect instanceof InterbaseDialect) {
            return "interbase";
        } else {
            return null;
        }
    }
    
    @Override
    public EntityManagerFactory registerFunctions(EntityManagerFactory entityManagerFactory, Map<String, JpqlFunctionGroup> dbmsFunctions) {
        EntityManager em = null;
        
        try {
            em = entityManagerFactory.createEntityManager();
            Session s = em.unwrap(Session.class);
            Map<String, SQLFunction> originalFunctions = getFunctions(s);
            Map<String, SQLFunction> functions = new TreeMap<String, SQLFunction>(String.CASE_INSENSITIVE_ORDER);
            functions.putAll(originalFunctions);
            Dialect dialect = getDialect(s);
            String dbms = getDbmsName(dialect);
            
            for (Map.Entry<String, JpqlFunctionGroup> functionEntry : dbmsFunctions.entrySet()) {
                String functionName = functionEntry.getKey();
                JpqlFunctionGroup dbmsFunctionMap = functionEntry.getValue();
                JpqlFunction function = dbmsFunctionMap.get(dbms);
                
                if (function == null && !dbmsFunctionMap.contains(dbms)) {
                    function = dbmsFunctionMap.get(null);
                }
                if (function == null) {
                    LOG.warning("Could not register the function '" + functionName + "' because there is neither an implementation for the dbms '" + dbms + "' nor a default implementation!");
                } else {
                    functions.put(functionName, new HibernateJpqlFunctionAdapter(function));
                }
            }
            
            replaceFunctions(s, functions);
            
            return entityManagerFactory;
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }

    @Override
    public Set<String> getRegisteredFunctions(EntityManagerFactory entityManagerFactory) {
        EntityManager em = null;
        
        try {
            em = entityManagerFactory.createEntityManager();
            Session s = em.unwrap(Session.class);
            return getFunctions(s).keySet();
        } finally {
            if (em != null) {
                em.close();
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, SQLFunction> getFunctions(Session s) {
        if (MAJOR < 5 || (MAJOR == 5 && MINOR == 0 && FIX == 0 && "Beta1".equals(TYPE))) {
            // Implementation detail: Hibernate uses a mutable map, so we can do this
            return getDialect(s).getFunctions();
        } else {
            SessionFactoryImplementor sf = (SessionFactoryImplementor) s.getSessionFactory();
            SQLFunctionRegistry registry = sf.getSqlFunctionRegistry();
            Exception ex;
            
            // We have to retrieve the functionMap the old fashioned way via reflection :(
            Field f = null;
            boolean madeAccessible = false;
            
            try {
                f = SQLFunctionRegistry.class.getDeclaredField("functionMap");
                madeAccessible = !f.isAccessible();

                if (madeAccessible) {
                    f.setAccessible(true);
                }

                return (Map<String, SQLFunction>) f.get(registry);
            } catch (NoSuchFieldException e) {
                ex = e;
            } catch (IllegalArgumentException e) {
                // This can never happen
                ex = e;
            } catch (IllegalAccessException e) {
                ex = e;
            } finally {
                if (f != null && madeAccessible) {
                    f.setAccessible(false);
                }
            }
            
            throw new RuntimeException("Could not access the function map to dynamically register functions. Please report this version of hibernate(" + VERSION_STRING + ") so we can provide support for it!", ex);
        }
    }
    
    private void replaceFunctions(Session s, Map<String, SQLFunction> newFunctions) {
        if (MAJOR < 5 || (MAJOR == 5 && MINOR == 0 && FIX == 0 && "Beta1".equals(TYPE))) {
            Exception ex;
            Field f = null;
            boolean madeAccessible = false;
            
            try {
                f = Dialect.class.getDeclaredField("sqlFunctions");
                madeAccessible = !f.isAccessible();
                
                if (madeAccessible) {
                    f.setAccessible(true);
                }
                
                f.set(getDialect(s), newFunctions);
                return;
            } catch (NoSuchFieldException e) {
                ex = e;
            } catch (IllegalArgumentException e) {
                // This can never happen
                ex = e;
            } catch (IllegalAccessException e) {
                ex = e;
            } finally {
                if (f != null && madeAccessible) {
                    f.setAccessible(false);
                }
            }
            throw new RuntimeException("Could not access the function map to dynamically register functions. Please report this version of hibernate(" + VERSION_STRING + ") so we can provide support for it!", ex);
        } else {
            SessionFactoryImplementor sf = (SessionFactoryImplementor) s.getSessionFactory();
            SQLFunctionRegistry registry = sf.getSqlFunctionRegistry();
            Exception ex;
            
            // We have to retrieve the functionMap the old fashioned way via reflection :(
            Field f = null;
            boolean madeAccessible = false;
            
            try {
                f = SQLFunctionRegistry.class.getDeclaredField("functionMap");
                madeAccessible = !f.isAccessible();
                
                if (madeAccessible) {
                    f.setAccessible(true);
                }
                
                f.set(registry, newFunctions);
                return;
            } catch (NoSuchFieldException e) {
                ex = e;
            } catch (IllegalArgumentException e) {
                // This can never happen
                ex = e;
            } catch (IllegalAccessException e) {
                ex = e;
            } finally {
                if (f != null && madeAccessible) {
                    f.setAccessible(false);
                }
            }
            
            throw new RuntimeException("Could not access the function map to dynamically register functions. Please report this version of hibernate(" + VERSION_STRING + ") so we can provide support for it!", ex);
        }
    }
    
    private Dialect getDialect(Session s) {
        SessionFactoryImplementor sf = (SessionFactoryImplementor) s.getSessionFactory();
        return sf.getDialect();
    }
}
