/*
 *  GeoBatch - Open Source geospatial batch processing system
 *  http://geobatch.codehaus.org/
 *  Copyright (C) 2007-2012 GeoSolutions S.A.S.
 *  http://www.geo-solutions.it
 *
 *  GPLv3 + Classpath exception
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.geosolutions.geobatch.services.jmx;

import it.geosolutions.filesystemmonitor.monitor.FileSystemEvent;
import it.geosolutions.filesystemmonitor.monitor.FileSystemEventType;
import it.geosolutions.geobatch.catalog.Catalog;
import it.geosolutions.geobatch.catalog.file.FileBaseCatalog;
import it.geosolutions.geobatch.configuration.event.action.ActionConfiguration;
import it.geosolutions.geobatch.configuration.event.consumer.file.FileBasedEventConsumerConfiguration;
import it.geosolutions.geobatch.configuration.flow.file.FileBasedFlowConfiguration;
import it.geosolutions.geobatch.flow.event.action.ActionService;
import it.geosolutions.geobatch.flow.event.consumer.file.FileBasedEventConsumer;
import it.geosolutions.geobatch.flow.file.FileBasedFlowManager;
import it.geosolutions.geobatch.global.CatalogHolder;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Resource;

import org.apache.commons.beanutils.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * 
 * JMX service which supports:<ul>
 * <li>
 * - creating JMX flow on the fly
 * </li>
 * <li>
 * - creating and starting consumers with externally configured action
 * </li>
 * <li>
 * - get status of JMX consumer instances 
 * </li>
 * <li>
 * - dispose JMX consumer instances 
 * </li>
 * <li>
 * - get status of JMX consumer instance 
 * </li>
 * </ul>
 * 
 * @author Carlo Cancellieri - carlo.cancellieri@geo-solutions.it
 * 
 */
@ManagedResource(objectName = "bean:name=JMXServiceManager", description = "JMX Service Manager to start/monitor/dispose GeoBatch action", log = true, logFile = "jmx.log", currencyTimeLimit = 15, persistPolicy = "OnUpdate", persistPeriod = 200, persistLocation = "foo", persistName = "JMXServiceManager")
public class JMXServiceManager implements ActionManager {
    private final static Logger LOGGER = LoggerFactory.getLogger(JMXServiceManager.class);

    public final static String FlowManagerID = "JMX_FLOW_MANAGER";

    private static Catalog catalog;

    private static FileBasedFlowManager flowManager;

    final FileBasedFlowConfiguration flowManagerConfig;

    private static File configDirFile;

    @Resource(type = org.springframework.context.ApplicationContext.class)
    private ApplicationContext context;

    public JMXServiceManager() throws Exception {
        catalog = CatalogHolder.getCatalog();

        flowManager = catalog.getResource(FlowManagerID,
                                          it.geosolutions.geobatch.flow.file.FileBasedFlowManager.class);
        if (flowManager == null) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("The flow id \'" + FlowManagerID
                            + "\' does not exists into catalog... -> going to create it");
            }
            flowManagerConfig = new FileBasedFlowConfiguration(FlowManagerID, FlowManagerID, "Auto generated " + FlowManagerID,
                        null, null);
            configDirFile = new File(((FileBaseCatalog)catalog).getConfigDirectory(), FlowManagerID);
            if (!configDirFile.exists()) {
                if (!(configDirFile.getParentFile().canWrite() && configDirFile.mkdir())) {
                    throw new IllegalArgumentException("Unable to automatically create the " + FlowManagerID
                                                       + " working dir into:"
                                                       + configDirFile.getAbsolutePath().toString());
                }
            }
//            flowManagerConfig.setWorkingDirectory(configDirFile.getAbsolutePath());

            flowManager = new FileBasedFlowManager(flowManagerConfig);

            catalog.add(flowManager);
            // TODO persistence (throws NullPointerException)
            // catalog.save(parent);
            // parent.persist();

        } else {
            configDirFile = flowManager.getWorkingDirectory();
            flowManagerConfig = flowManager.getConfiguration();
        }

        if (!configDirFile.exists()) {
            if (!(configDirFile.getParentFile().canWrite() && configDirFile.mkdir())) {
                throw new IllegalArgumentException("Unable to automatically create the " + FlowManagerID
                                                   + " working dir into:"
                                                   + configDirFile.getAbsolutePath().toString());
            }
        }

        // TODO listener config
        // if ()
        // flowManagerConfig.getProgressListenerConfigurations();

    }

    /**
     * returns the status of the selected consumer
     * 
     * @param uuid
     * @return {@link ConsumerStatus}
     */
    @Override
    @org.springframework.jmx.export.annotation.ManagedOperation(description = "disposeAction - used to dispose the consumer instance from the consumer registry")
    @ManagedOperationParameters({@ManagedOperationParameter(name = "uuid", description = "The uuid of the consumer")})
    public void disposeAction(String uuid) throws Exception {
        flowManager.disposeConsumer(uuid);
    }

    /**
     * returns the status of the selected consumer
     * 
     * @param uuid
     * @return {@link ConsumerStatus}
     */
    @Override
    @org.springframework.jmx.export.annotation.ManagedOperation(description = "get the status of the selected consumer")
    @ManagedOperationParameters({@ManagedOperationParameter(name = "uuid", description = "The uuid of the consumer")})
    public ConsumerStatus getStatus(String uuid) {
        return ConsumerStatus.getStatus(flowManager.getStatus(uuid));
    }

    @Override
    @org.springframework.jmx.export.annotation.ManagedOperation(description = "callAction - used to run a consumer")
    @ManagedOperationParameters({@ManagedOperationParameter(name = "config", description = "A map containing the list of needed paramethers, inputs and outputs used by the action")})
    public String callAction(java.util.Map<String, String> config) throws Exception {
        final String serviceId = config.remove(SERVICE_ID_KEY);
        if (serviceId == null || serviceId.isEmpty())
            throw new IllegalArgumentException(
                                               "Unable to locate the key "
                                                   + SERVICE_ID_KEY
                                                   + " matching the serviceId action in the passed paramether table");

        final String input = config.remove(INPUT_KEY);
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Unable to locate the key " + INPUT_KEY
                                               + " matching input in the passed paramether table.");
        }
        FileSystemEvent event = new FileSystemEvent(new File(input), FileSystemEventType.FILE_ADDED);
        Queue<FileSystemEvent> events = new java.util.LinkedList<FileSystemEvent>();
        events.add(event);

        // TODO remove all 'NOT configuration' param

        return callAction(serviceId, config, events);
    };

    private String callAction(String serviceId, Map<String, String> config, Queue<FileSystemEvent> events)
        throws Exception {

        final ActionService service = (ActionService)context.getBean(serviceId);
        final Class serviceClass = service.getClass();

        ActionConfiguration actionConfig = null;
        for (Method method : serviceClass.getMethods()) {
            if (method.getName().equals("canCreateAction")) {
                final Class[] classes = method.getParameterTypes();
                final Constructor constructor = classes[0].getConstructor(new Class[] {String.class,
                                                                                       String.class,
                                                                                       String.class});
                actionConfig = (ActionConfiguration)constructor.newInstance(UUID.randomUUID().toString(),
                                                                            "NAME", "DESC");
                actionConfig.setServiceID(serviceId);
                final Set<String> keys = config.keySet();
                final Iterator<String> it = keys.iterator();
                while (it.hasNext()) {
                    String key = it.next();
                    try {
                        smartCopy(actionConfig, key, config.get(key));
                    } catch (Exception e) {
                        if (LOGGER.isErrorEnabled())
                            LOGGER.error(e.getLocalizedMessage(), e);
                        // TODO something else?
                    }
                }
                actionConfig.setConfigDir(configDirFile);

                if (actionConfig != null)
                    break;
                // BeanUtils.instantiate(clazz)
            }
        }
        if (actionConfig == null)
            throw new IllegalArgumentException("Unable to locate the configuration");

        final FileBasedEventConsumerConfiguration consumerConfig = new FileBasedEventConsumerConfiguration(
                                                                                                           "JMX_Consumer_id"
//                                                                                                           ,"JMX_Consumer_name"
//                                                                                                           ,"JMX_Consumer description"
                );
        // TODO Status progress listener
        // final StatusProgressListenerConfiguration
        // statusProgressListenerConfig=new
        // StatusProgressListenerConfiguration("status_listener",
        // "status_listener", "status_listener");
        // statusProgressListenerConfig.setServiceID("StatusProgressListener");
        // actionConfig.addListenerConfiguration(statusProgressListenerConfig);

        final List<ActionConfiguration> actions = new ArrayList<ActionConfiguration>();
        actions.add(actionConfig);

        consumerConfig.setActions(actions);
//        consumerConfig.setWorkingDirectory(configDirFile.getAbsolutePath());
        // TODO may we want to remove only when getStatus is remotely called???
        // consumerConfig.setKeepContextDir(true);

        // if you whant to move the input you may call the action move!
        consumerConfig.setPreserveInput(true);

        // TODO logging progress listener
        // final LoggingProgressListenerConfiguration
        // loggingProgressListenerConfig=new
        // LoggingProgressListenerConfiguration("logging_listener",
        // "logging_listener", "logging_listener");
        // loggingProgressListenerConfig.setServiceID("LoggingProgressListener");
        // loggingProgressListenerConfig.setLoggerName("it.geosolutions.geobatch.services");
        // consumerConfig.addListenerConfiguration(loggingProgressListenerConfig);

        final FileBasedEventConsumer consumer = new FileBasedEventConsumer(consumerConfig, flowManagerConfig );
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("INIT injecting consumer to the parent flow. UUID: " + consumer.getId());
        }

        // following ops are atomic
        synchronized (flowManager) {
            flowManager.addConsumer(consumer);

            for (FileSystemEvent event : events) {
                consumer.consume(event);
            }

            // execute
            flowManager.getExecutor().submit(consumer);
        }

        return consumer.getId();
    }

    private static <T> void smartCopy(final T bean, final String propertyName, final String value)
        throws Exception {
        PropertyDescriptor pd = PropertyUtils.getPropertyDescriptor(bean, propertyName);
        // return null if there is no such descriptor
        if (pd == null) {
            return;
        }
        // T interface doesn't declare setter method for this property
        // lets use getter methods to get the property reference
        final Object property = PropertyUtils.getProperty(bean, propertyName);

        // check type of property to apply new value
        if (Collection.class.isAssignableFrom(pd.getPropertyType())) {

            final Collection<Object> liveCollection;
            if (property != null) {
                liveCollection = (Collection<Object>)property;
                liveCollection.clear();
            } else {
                liveCollection = new LinkedList<Object>();
            }

            // value should be a list of string ',' separated
            String[] listString = value.split(",");
            for (String s : listString) {
                liveCollection.add(s);
            }

        } else if (Map.class.isAssignableFrom(pd.getPropertyType())) {

            final Map<Object, Object> liveMap;
            if (property != null) {
                liveMap = (Map<Object, Object>)property;
                liveMap.clear();
            } else {
                liveMap = new HashMap<Object, Object>();
            }

            // value should be a list of key=value string ';' separated
            String[] listString = value.split(";");
            for (String kvString : listString) {
                String kv[] = kvString.split("=");
                liveMap.put(kv[0], kv[1]);
            }

        } else {
            if (pd.getWriteMethod() != null) {
                PropertyUtils.setProperty(bean, propertyName, value);
            } else {
                if (LOGGER.isErrorEnabled())
                    LOGGER.error("Skipping unwritable property " + propertyName + " with property type "
                                 + pd.getPropertyType());
            }
        }
    }

    // @Override
    // public void setApplicationContext(ApplicationContext applicationContext)
    // throws BeansException {
    // context=applicationContext;
    // }

}
