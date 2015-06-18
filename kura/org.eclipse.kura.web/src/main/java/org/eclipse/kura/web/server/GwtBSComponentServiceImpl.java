/**
 * Copyright (c) 2011, 2014 Eurotech and/or its affiliates
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Eurotech
 */
package org.eclipse.kura.web.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.kura.configuration.ComponentConfiguration;
import org.eclipse.kura.configuration.ConfigurationService;
import org.eclipse.kura.configuration.metatype.AD;
import org.eclipse.kura.configuration.metatype.Icon;
import org.eclipse.kura.configuration.metatype.OCD;
import org.eclipse.kura.configuration.metatype.Option;
import org.eclipse.kura.configuration.Password;
import org.eclipse.kura.web.server.util.KuraExceptionHandler;
import org.eclipse.kura.web.server.util.ServiceLocator;
import org.eclipse.kura.web.shared.GwtKuraException;
import org.eclipse.kura.web.shared.model.GwtBSConfigComponent;
import org.eclipse.kura.web.shared.model.GwtBSConfigParameter;
import org.eclipse.kura.web.shared.model.GwtBSConfigParameter.GwtBSConfigParameterType;
import org.eclipse.kura.web.shared.service.GwtBSComponentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GwtBSComponentServiceImpl extends OsgiRemoteServiceServlet
		implements GwtBSComponentService {
	private static final long serialVersionUID = -4176701819112753800L;

	private static final Logger s_logger = LoggerFactory
			.getLogger(GwtBSComponentServiceImpl.class);

	public ArrayList<GwtBSConfigComponent> findComponentConfigurations()
			throws GwtKuraException {
		ConfigurationService cs = ServiceLocator.getInstance().getService(
				ConfigurationService.class);
		ArrayList<GwtBSConfigComponent> gwtConfigs = new ArrayList<GwtBSConfigComponent>();
		try {

			ArrayList<ComponentConfiguration> configs = (ArrayList<ComponentConfiguration>) cs
					.getComponentConfigurations();
			// sort the list alphabetically by service name
			Collections.sort(configs, new Comparator<ComponentConfiguration>() {
				public int compare(ComponentConfiguration arg0,
						ComponentConfiguration arg1) {
					String name0 = arg0.getPid().substring(
							arg0.getPid().lastIndexOf("."));
					String name1 = arg1.getPid().substring(
							arg1.getPid().lastIndexOf("."));
					return name0.compareTo(name1);
				}
			});
			for (ComponentConfiguration config : configs) {

				// ignore items we want to hide
				if (config.getPid().endsWith("SystemPropertiesService")
						|| config.getPid().endsWith("NetworkAdminService")
						|| config.getPid().endsWith(
								"NetworkConfigurationService")) {
					continue;
				}

				OCD ocd = config.getDefinition();
				if (ocd != null) {

					GwtBSConfigComponent gwtConfig = new GwtBSConfigComponent();
					gwtConfig.setComponentId(ocd.getId());
					gwtConfig.setComponentName(ocd.getName());
					gwtConfig.setComponentDescription(ocd.getDescription());
					if (ocd.getIcon() != null && ocd.getIcon().size() > 0) {
						Icon icon = ocd.getIcon().get(0);
						gwtConfig.setComponentIcon(icon.getResource());
					}

					ArrayList<GwtBSConfigParameter> gwtParams = new ArrayList<GwtBSConfigParameter>();
					gwtConfig.setParameters(gwtParams);
					for (AD ad : ocd.getAD()) {

						GwtBSConfigParameter gwtParam = new GwtBSConfigParameter();
						gwtParam.setId(ad.getId());
						gwtParam.setName(ad.getName());
						gwtParam.setDescription(ad.getDescription());
						gwtParam.setType(GwtBSConfigParameterType.valueOf(ad
								.getType().name()));
						gwtParam.setRequired(ad.isRequired());
						gwtParam.setCardinality(ad.getCardinality());
						if (ad.getOption() != null && ad.getOption().size() > 0) {
							Map<String, String> options = new HashMap<String, String>();
							for (Option option : ad.getOption()) {
								options.put(option.getLabel(),
										option.getValue());
							}
							gwtParam.setOptions(options);
						}
						gwtParam.setMin(ad.getMin());
						gwtParam.setMax(ad.getMax());
						if (config.getConfigurationProperties() != null) {

							// handle the value based on the cardinality of the
							// attribute
							int cardinality = ad.getCardinality();
							Object value = config.getConfigurationProperties()
									.get(ad.getId());
							if (value != null) {
								if (cardinality == 0 || cardinality == 1
										|| cardinality == -1) {
									gwtParam.setValue(value.toString());
								} else {
									// this could be an array value
									if (value instanceof Object[]) {
										Object[] objValues = (Object[]) value;
										ArrayList<String> strValues = new ArrayList<String>();
										for (Object v : objValues) {
											if (v != null) {
												strValues.add(v.toString());
											}
										}
										gwtParam.setValues(strValues
												.toArray(new String[] {}));
									}
								}
							}
							gwtParams.add(gwtParam);
						}
					}
					gwtConfigs.add(gwtConfig);
				}
			}
		} catch (Throwable t) {
			s_logger.error("------" + t.getLocalizedMessage());
			KuraExceptionHandler.handle(t);
		}

		s_logger.info("Component service returning " + gwtConfigs.size()
				+ " Rows");

		// FIXME DEV ONLY--------------------------------------------
		try {
			File file = new File("/Users/rohit/Documents/logFile.txt");
			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			} else {
				file.delete();
				file.createNewFile();
			}
			FileOutputStream fop = new FileOutputStream(file);
			ObjectOutputStream oos = new ObjectOutputStream(fop);

			oos.writeObject(gwtConfigs);

			oos.close();
		} catch (IOException e) {

		}
		// -----------------------------------------------------
		return gwtConfigs;
	}

	public ArrayList<GwtBSConfigComponent> findComponentConfiguration()
			throws GwtKuraException {
		ConfigurationService cs = ServiceLocator.getInstance().getService(
				ConfigurationService.class);
		ArrayList<GwtBSConfigComponent> gwtConfigs = new ArrayList<GwtBSConfigComponent>();
		try {

			ArrayList<ComponentConfiguration> configs = (ArrayList<ComponentConfiguration>) cs
					.getComponentConfigurations();
			// sort the list alphabetically by service name
			Collections.sort(configs, new Comparator<ComponentConfiguration>() {
				public int compare(ComponentConfiguration arg0,
						ComponentConfiguration arg1) {
					String name0 = arg0.getPid().substring(
							arg0.getPid().lastIndexOf("."));
					String name1 = arg1.getPid().substring(
							arg1.getPid().lastIndexOf("."));
					return name0.compareTo(name1);
				}
			});
			for (ComponentConfiguration config : configs) {

				// ignore items we want to hide
				if (!config.getPid().endsWith("CommandCloudApp")) {
					continue;
				}

				OCD ocd = config.getDefinition();
				if (ocd != null) {

					GwtBSConfigComponent gwtConfig = new GwtBSConfigComponent();
					gwtConfig.setComponentId(ocd.getId());
					gwtConfig.setComponentName(ocd.getName());
					gwtConfig.setComponentDescription(ocd.getDescription());
					if (ocd.getIcon() != null && ocd.getIcon().size() > 0) {
						Icon icon = ocd.getIcon().get(0);
						gwtConfig.setComponentIcon(icon.getResource());
					}

					ArrayList<GwtBSConfigParameter> gwtParams = new ArrayList<GwtBSConfigParameter>();
					gwtConfig.setParameters(gwtParams);
					for (AD ad : ocd.getAD()) {

						GwtBSConfigParameter gwtParam = new GwtBSConfigParameter();
						gwtParam.setId(ad.getId());
						gwtParam.setName(ad.getName());
						gwtParam.setDescription(ad.getDescription());
						gwtParam.setType(GwtBSConfigParameterType.valueOf(ad
								.getType().name()));
						gwtParam.setRequired(ad.isRequired());
						gwtParam.setCardinality(ad.getCardinality());
						if (ad.getOption() != null && ad.getOption().size() > 0) {
							Map<String, String> options = new HashMap<String, String>();
							for (Option option : ad.getOption()) {
								options.put(option.getLabel(),
										option.getValue());
							}
							gwtParam.setOptions(options);
						}
						gwtParam.setMin(ad.getMin());
						gwtParam.setMax(ad.getMax());
						if (config.getConfigurationProperties() != null) {

							// handle the value based on the cardinality of the
							// attribute
							int cardinality = ad.getCardinality();
							Object value = config.getConfigurationProperties()
									.get(ad.getId());
							if (value != null) {
								if (cardinality == 0 || cardinality == 1
										|| cardinality == -1) {
									gwtParam.setValue(value.toString());
								} else {
									// this could be an array value
									if (value instanceof Object[]) {
										Object[] objValues = (Object[]) value;
										ArrayList<String> strValues = new ArrayList<String>();
										for (Object v : objValues) {
											if (v != null) {
												strValues.add(v.toString());
											}
										}
										gwtParam.setValues(strValues
												.toArray(new String[] {}));
									}
								}
							}
							gwtParams.add(gwtParam);
						}
					}
					gwtConfigs.add(gwtConfig);
				}
			}
		} catch (Throwable t) {
			KuraExceptionHandler.handle(t);
		}
		return gwtConfigs;
	}

	public void updateComponentConfiguration(GwtBSConfigComponent gwtCompConfig)
			throws GwtKuraException {
		ConfigurationService cs = ServiceLocator.getInstance().getService(
				ConfigurationService.class);
		try {

			// Build the new properties
			Map<String, Object> properties = new HashMap<String, Object>();
			for (GwtBSConfigParameter gwtConfigParam : gwtCompConfig
					.getParameters()) {

				Object objValue = null;
				int cardinality = gwtConfigParam.getCardinality();
				if (cardinality == 0 || cardinality == 1 || cardinality == -1) {

					String strValue = gwtConfigParam.getValue();
					objValue = getObjectValue(gwtConfigParam, strValue);
				} else {

					String[] strValues = gwtConfigParam.getValues();
					objValue = getObjectValue(gwtConfigParam, strValues);
				}
				properties.put(gwtConfigParam.getName(), objValue);
			}

			//
			// apply them
			cs.updateConfiguration(gwtCompConfig.getComponentId(), properties);
		} catch (Throwable t) {
			KuraExceptionHandler.handle(t);
		}
	}

	private Object getObjectValue(GwtBSConfigParameter gwtConfigParam,
			String strValue) {
		Object objValue = null;
		if (strValue != null) {
			GwtBSConfigParameterType gwtType = gwtConfigParam.getType();
			switch (gwtType) {
			case LONG:
				objValue = Long.parseLong(strValue);
				break;
			case DOUBLE:
				objValue = Double.parseDouble(strValue);
				break;
			case FLOAT:
				objValue = Float.parseFloat(strValue);
				break;
			case INTEGER:
				objValue = Integer.parseInt(strValue);
				break;
			case SHORT:
				objValue = Short.parseShort(strValue);
				break;
			case BYTE:
				objValue = Byte.parseByte(strValue);
				break;

			case BOOLEAN:
				objValue = Boolean.parseBoolean(strValue);
				break;

			case PASSWORD:
				objValue = new Password(strValue);
				break;

			case CHAR:
				objValue = Character.valueOf(strValue.charAt(0));
				break;

			case STRING:
				objValue = strValue;
				break;
			}
		}
		return objValue;
	}

	private Object[] getObjectValue(GwtBSConfigParameter gwtConfigParam,
			String[] defaultValues) {
		ArrayList<Object> values = new ArrayList<Object>();
		GwtBSConfigParameterType type = gwtConfigParam.getType();
		switch (type) {
		case BOOLEAN:
			for (String value : defaultValues) {
				values.add(Boolean.valueOf(value));
			}
			return values.toArray(new Boolean[] {});

		case BYTE:
			for (String value : defaultValues) {
				values.add(Byte.valueOf(value));
			}
			return values.toArray(new Byte[] {});

		case CHAR:
			for (String value : defaultValues) {
				values.add(new Character(value.charAt(0)));
			}
			return values.toArray(new Character[] {});

		case DOUBLE:
			for (String value : defaultValues) {
				values.add(Double.valueOf(value));
			}
			return values.toArray(new Double[] {});

		case FLOAT:
			for (String value : defaultValues) {
				values.add(Float.valueOf(value));
			}
			return values.toArray(new Float[] {});

		case INTEGER:
			for (String value : defaultValues) {
				values.add(Integer.valueOf(value));
			}
			return values.toArray(new Integer[] {});

		case LONG:
			for (String value : defaultValues) {
				values.add(Long.valueOf(value));
			}
			return values.toArray(new Long[] {});

		case SHORT:
			for (String value : defaultValues) {
				values.add(Short.valueOf(value));
			}
			return values.toArray(new Short[] {});

		case PASSWORD:
			for (String value : defaultValues) {
				values.add(new Password(value));
			}
			return values.toArray(new Password[] {});

		case STRING:
			return defaultValues;
		}

		return null;
	}
}
