/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.datacomparison.api.impl;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hibernate.Hibernate;
import org.openmrs.OpenmrsObject;
import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.datacomparison.DataComparisonConsts;
import org.openmrs.module.datacomparison.ElementMeta;
import org.openmrs.module.datacomparison.RowMeta;
import org.openmrs.module.datacomparison.api.MetaDataComparisonService;
import org.openmrs.util.Reflect;

/**
 * Default implementation of {@link MetaDataComparisonService}.
 * <p>
 * This class should not be used on its own. The current implementation should be fetched from the
 * Context via <code>Context.getService(MetaDataComparisonService.class)</code>
 * 
 * @see org.openmrs.api.context.Context
 * @see org.openmrs.module.datacomparison.api.MetaDataComparisonService
 */
public class MetaDataComparisonServiceImpl extends BaseOpenmrsService implements MetaDataComparisonService {
	
	/**
	 * @throws SecurityException 
	 * @throws NoSuchFieldException 
	 * @see org.openmrs.module.datacomparison.api.MetaDataComparisonService#getRowMetaList(java.lang.Object, java.lang.Object)
	 */
	@SuppressWarnings("rawtypes")
    public List<RowMeta> getRowMetaList(Object existingItem, Object incomingItem) throws APIException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
		
		List<RowMeta> rowMetaList = new ArrayList<RowMeta>();
		
		ElementMeta existingItemMeta = null;
		ElementMeta incomingItemMeta = null;
		RowMeta rowMeta = null;
		Map<String, ElementMeta> metaItems = null;
		
		Class c = incomingItem.getClass();
		List<Field> fields = Reflect.getAllFields(c);
		
		for (int i=0; i<fields.size(); i++) {
			
			existingItemMeta = new ElementMeta();
			incomingItemMeta = new ElementMeta();
			rowMeta = new RowMeta();
			metaItems = new HashMap<String, ElementMeta>();
			
			fields.get(i).setAccessible(true);
			
			Object existingItemFieldValue = fields.get(i).get(existingItem);
			Object incomingItemFieldValue = fields.get(i).get(incomingItem);
			
			if (existingItemFieldValue != null) {
				
				existingItemMeta = getElementMetaObjectAfterSetProperties(existingItemMeta, existingItemFieldValue);
                
            } else {
            	existingItemMeta.setIsComplex(false);
            	existingItemMeta.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
            	existingItemMeta.setPropertyValue(DataComparisonConsts.NULL);
            }
			
			if (incomingItemFieldValue != null) {
				
				incomingItemMeta = getElementMetaObjectAfterSetProperties(incomingItemMeta, incomingItemFieldValue);
                
			} else {
				incomingItemMeta.setIsComplex(false);
            	incomingItemMeta.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
            	incomingItemMeta.setPropertyValue(DataComparisonConsts.NULL);
			}
			try {
				if ((existingItemFieldValue != null) && (incomingItemFieldValue != null)) {
					if (existingItemFieldValue.equals(incomingItemFieldValue)) {
	                	rowMeta.setIsSimilar(true);
	                } else {
	                	rowMeta.setIsSimilar(false);
	                }
				} else if ((existingItemFieldValue == null) && (incomingItemFieldValue == null)) {
					rowMeta.setIsSimilar(true);
				} else {
					rowMeta.setIsSimilar(false);
				}
			}catch(Exception e) {
				e.printStackTrace();
			}
			metaItems.put("existingItem", existingItemMeta);
			metaItems.put("incomingItem", incomingItemMeta);
			rowMeta.setMetaItems(metaItems);
			
			if (existingItemMeta.getIsComplex() || incomingItemMeta.getIsComplex()) {
				rowMeta = getChildElementMeta(rowMeta, existingItemFieldValue, incomingItemFieldValue, fields.get(i));
			}
			
			rowMeta.setPropertyName(fields.get(i).getName());
			rowMeta.setLevel(0);
			
			rowMetaList.add(rowMeta);
			
		}
		
		return rowMetaList;
		
	}
	
	/**
	 * @see org.openmrs.module.datacomparison.api.MetaDataComparisonService#getChildElementMeta(
	 * 		java.util.Map<String, ElementMeta>, java.lang.Object, java.lang.Object, java.lang.Class, java.lang.String)
	 */
	public RowMeta getChildElementMeta(
		RowMeta rowMeta, Object existingItemPropertyValue, Object incomingItemPropertyValue, Field field
	) throws APIException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		
		Map<String, ElementMeta> metaItems = rowMeta.getMetaItems();
		
		ElementMeta existingElementMeta = rowMeta.getMetaItems().get("existingItem");
		ElementMeta incomingElementMeta = rowMeta.getMetaItems().get("incomingItem");
		
		List<ElementMeta> existingSubElmentMetaList = null;
		List<ElementMeta> incomingSubElmentMetaList = null;
		
		// Both property values are not null
		if ((existingItemPropertyValue != null) && (incomingItemPropertyValue != null)) {
			
			// Collection data type
			if (existingElementMeta.getPropertyType() == DataComparisonConsts.COLLECTION_DATA_TYPE) {
				
				Collection<?> existingCollectionProperty = (Collection<?>) existingItemPropertyValue;
				Collection<?> incomingCollectionProperty = (Collection<?>) incomingItemPropertyValue;
				
				Class genericClass = (Class) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
				
				// Both properties are not empty
				if ((existingCollectionProperty.size() > 0) && (incomingCollectionProperty.size() > 0)) {
					
					// Both collections are equal case
					if (existingCollectionProperty.containsAll(incomingCollectionProperty)
						&& incomingCollectionProperty.containsAll(existingCollectionProperty)
					) {
						
						// If elements of the collection are simple data
						if (isSimpleDataType(null, genericClass)) {
							
							existingSubElmentMetaList = new ArrayList<ElementMeta>();
							
							// Just iterate the items in collection
							for (Object obj : existingCollectionProperty) {
								
								ElementMeta em = new ElementMeta();
								
								em.setIsComplex(false);
								em.setLevel(1);
								em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
								em.setPropertyValue(obj.toString());
								em.setSubElmentMetaList(null);
								em.setIsSimilar(true);
								
								existingSubElmentMetaList.add(em);
								
							}
							
							// Since both are same
							existingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
							incomingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
							
							metaItems.put("existingItem", existingElementMeta);
							metaItems.put("incomingItem", incomingElementMeta);
							
							rowMeta.setMetaItems(metaItems);
							rowMeta.setIsSimilar(true);
							
						} else if (isOpenMrsObject(null, genericClass)) {
							
							// If elements of the collection are OpenMRS object type
							
							ElementMeta childSubElementMeta;
							ElementMeta childElementMeta;
							
							List<ElementMeta> childElementMetaList = new ArrayList<ElementMeta>();
							List<ElementMeta> childSubElementMetaList = new ArrayList<ElementMeta>();
							
							
							List<Field> childObjectFields = Reflect.getAllFields(genericClass);
							
							for (Object obj : existingCollectionProperty) {
								
								childElementMeta = new ElementMeta();
								
								for (int a=0; a<childObjectFields.size(); a++) {
									
									childObjectFields.get(a).setAccessible(true);
									
									childSubElementMeta = new ElementMeta();
									
									childSubElementMeta.setIsSimilar(true);
									childSubElementMeta.setPropertyName(childObjectFields.get(a).getName());
									childSubElementMeta.setLevel(2);
									childSubElementMeta.setSubElmentMetaList(null);
									
									if (childObjectFields.get(a).get(obj) != null) {
										if (isSimpleDataType(childObjectFields.get(a).get(obj), null)) {
											childSubElementMeta.setIsComplex(false);
											childSubElementMeta.setPropertyValue(childObjectFields.get(a).get(obj).toString());
										} else {
											childSubElementMeta.setIsComplex(true);
											if (Reflect.isCollection(childObjectFields.get(a).get(obj))) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.COLLECTION_DATA_TYPE);
												childSubElementMeta.setPropertyValue("Collection data not described in this level");
											} else if (isOpenMrsObject(childObjectFields.get(a).get(obj), null)) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.OPENMRS_DATA_TYPE);
												childSubElementMeta.setPropertyValue("OpenMRS object not described in this level");
											} else if (isMap(childObjectFields.get(a).get(obj), null)) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.MAP_DATA_TYPE);
												childSubElementMeta.setPropertyValue("Map data not described in this level");
											} else {
												childSubElementMeta.setPropertyValue("Not identified");
											}
										}
									} else {
										childSubElementMeta.setIsComplex(false);
										childSubElementMeta.setPropertyValue(DataComparisonConsts.NULL);
									}
									
									childSubElementMetaList.add(childSubElementMeta);
									
								}
								
								childElementMeta.setIsSimilar(true);
								childElementMeta.setIsComplex(true);
								childElementMeta.setLevel(1);
								childElementMeta.setPropertyName("");
								childElementMeta.setSubElmentMetaList(childSubElementMetaList);
								
								childElementMetaList.add(childElementMeta);
								
							}
							
							existingElementMeta.setSubElmentMetaList(childElementMetaList);
							incomingElementMeta.setSubElmentMetaList(childElementMetaList);
							
							metaItems.put("existingItem", existingElementMeta);
							metaItems.put("incomingItem", incomingElementMeta);
							
							rowMeta.setMetaItems(metaItems);
							rowMeta.setIsSimilar(true);
							
						} else if (isOpenMrsObject(null, genericClass)) {
							
							int genericType = -1;
							
							for (Map.Entry<String, ?> entry : ((Map<String, ?>)existingItemPropertyValue).entrySet()) {
								
								if (isSimpleDataType(entry.getValue(), null)) {
									genericType = DataComparisonConsts.SIMPLE_DATA_TYPE;
								} else if (Reflect.isCollection(entry.getValue())) {
									genericType = DataComparisonConsts.COLLECTION_DATA_TYPE;
								} else if (isMap(entry.getValue(), null)) {
									genericType = DataComparisonConsts.MAP_DATA_TYPE;
								} else if (isOpenMrsObject(entry.getValue(), null)) {
									genericType = DataComparisonConsts.OPENMRS_DATA_TYPE;
								}
								
								break;
								
							}
							
							if (genericType == DataComparisonConsts.SIMPLE_DATA_TYPE) {
								
								existingSubElmentMetaList = new ArrayList<ElementMeta>();
								
								for (Map.Entry<String, ?> entry : ((Map<String, ?>)existingItemPropertyValue).entrySet()) {
									
									ElementMeta em = new ElementMeta();
									
									em.setIsComplex(false);
									em.setLevel(2);
									em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
									em.setPropertyName(entry.getKey());
									em.setPropertyValue(entry.getValue().toString());
									em.setSubElmentMetaList(null);
									em.setIsSimilar(true);
									
									existingSubElmentMetaList.add(em);
									
								}
								
								// Since both are same
								existingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
								incomingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
								
								metaItems.put("existingItem", existingElementMeta);
								metaItems.put("incomingItem", incomingElementMeta);
								
								rowMeta.setMetaItems(metaItems);
								rowMeta.setIsSimilar(true);
								
							} else {
								
								existingElementMeta.setPropertyValue("Complex data types are not described in this level");
								incomingElementMeta.setPropertyValue("Complex data types are not described in this level");
								
								metaItems.put("existingItem", existingElementMeta);
								metaItems.put("incomingItem", incomingElementMeta);
								
								rowMeta.setMetaItems(metaItems);
								rowMeta.setIsSimilar(true);
								
							}
							
						}
						
					} else {
						
						// Collections are not equal in this case
						
						// If elements of the collection are simple data
						if (isSimpleDataType(null, genericClass)) {
							
							List<String> similarDataList = new ArrayList<String>();
							List<ElementMeta> simiarElementMetaList = new ArrayList<ElementMeta>();
							ElementMeta em;
							
							for (Object obj : incomingCollectionProperty) {
								
								if (existingCollectionProperty.contains(obj)) {
									
									similarDataList.add(obj.toString());
									
									em = new ElementMeta();
									em.setIsComplex(false);
									em.setLevel(1);
									em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
									em.setPropertyValue(obj.toString());
									em.setSubElmentMetaList(null);
									em.setIsSimilar(true);
									
									simiarElementMetaList.add(em);
									
								}
								
							}
							
							existingSubElmentMetaList = new ArrayList<ElementMeta>();
							incomingSubElmentMetaList = new ArrayList<ElementMeta>();
							
							// Add similar items first to existingSubElmentMetaList and incomingSubElmentMetaList
							existingSubElmentMetaList.addAll(simiarElementMetaList);
							incomingSubElmentMetaList.addAll(simiarElementMetaList);
							
							// Remove similar items from the existingCollectionProperty and incomingCollectionProperty
							existingCollectionProperty.removeAll(similarDataList);
							incomingCollectionProperty.removeAll(similarDataList);
							
							// Add remaining items for the existingSubElmentMetaList
							for (Object obj : existingCollectionProperty) {
								
								em = new ElementMeta();
								em.setIsComplex(false);
								em.setLevel(1);
								em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
								em.setPropertyValue(obj.toString());
								em.setSubElmentMetaList(null);
								em.setIsSimilar(false);
								
								existingSubElmentMetaList.add(em);
								
							}
							
							// Add remaining items for the incomingSubElmentMetaList
							for (Object obj : incomingCollectionProperty) {
								
								em = new ElementMeta();
								em.setIsComplex(false);
								em.setLevel(1);
								em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
								em.setPropertyValue(obj.toString());
								em.setSubElmentMetaList(null);
								em.setIsSimilar(false);
								
								incomingSubElmentMetaList.add(em);
								
							}
							
							// Since both are same
							existingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
							incomingElementMeta.setSubElmentMetaList(incomingSubElmentMetaList);
							
							metaItems.put("existingItem", existingElementMeta);
							metaItems.put("incomingItem", incomingElementMeta);
							
							rowMeta.setMetaItems(metaItems);
							rowMeta.setIsSimilar(false);
							
						} else if (isOpenMrsObject(null, genericClass)) {
							
							// If elements of the collection are OpenMRS object type
							
							List<Object> similarObjectList = new ArrayList<Object>();
							//List<ElementMeta> simiarElementMetaList = new ArrayList<ElementMeta>();
							ElementMeta childSubElementMeta;
							
							ElementMeta childElementMeta;
							
							List<ElementMeta> childElementMetaList = new ArrayList<ElementMeta>();
							List<ElementMeta> existingDifferentChildElementMetaList = new ArrayList<ElementMeta>();
							List<ElementMeta> incomingDifferentChildElementMetaList = new ArrayList<ElementMeta>();
							
							List<ElementMeta> childSubElementMetaList = new ArrayList<ElementMeta>();
							
							for (Object obj : incomingCollectionProperty) {
								if (existingCollectionProperty.contains(obj)) {
									similarObjectList.add(obj);	
								}
							}
							
							existingCollectionProperty.removeAll(similarObjectList);
							incomingCollectionProperty.removeAll(similarObjectList);
							
							List<Field> childObjectFields = Reflect.getAllFields(genericClass);
							
							// Send similar obj list n get meta list
							
							for (Object obj : similarObjectList) {
								
								childElementMeta = new ElementMeta();
								
								for (int a=0; a<childObjectFields.size(); a++) {
									
									childObjectFields.get(a).setAccessible(true);
									
									childSubElementMeta = new ElementMeta();
									
									childSubElementMeta.setIsSimilar(true);
									childSubElementMeta.setPropertyName(childObjectFields.get(a).getName());
									childSubElementMeta.setLevel(2);
									childSubElementMeta.setSubElmentMetaList(null);
									
									if (childObjectFields.get(a).get(obj) != null) {
										if (isSimpleDataType(childObjectFields.get(a).get(obj), null)) {
											childSubElementMeta.setIsComplex(false);
											childSubElementMeta.setPropertyValue(childObjectFields.get(a).get(obj).toString());
										} else {
											childSubElementMeta.setIsComplex(true);
											if (Reflect.isCollection(childObjectFields.get(a).get(obj))) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.COLLECTION_DATA_TYPE);
												childSubElementMeta.setPropertyValue("Collection data not described in this level");
											} else if (isOpenMrsObject(childObjectFields.get(a).get(obj), null)) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.OPENMRS_DATA_TYPE);
												childSubElementMeta.setPropertyValue("OpenMRS object not described in this level");
											} else if (isMap(childObjectFields.get(a).get(obj), null)) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.MAP_DATA_TYPE);
												childSubElementMeta.setPropertyValue("Map data not described in this level");
											} else {
												childSubElementMeta.setPropertyValue("Not identified");
											}
										}
									} else {
										childSubElementMeta.setIsComplex(false);
										childSubElementMeta.setPropertyValue(DataComparisonConsts.NULL);
									}
									
									childSubElementMetaList.add(childSubElementMeta);
									
								}
								
								childElementMeta.setIsSimilar(true);
								childElementMeta.setIsComplex(true);
								childElementMeta.setLevel(1);
								childElementMeta.setPropertyName("");
								childElementMeta.setSubElmentMetaList(childSubElementMetaList);
								
								childElementMetaList.add(childElementMeta);
								
							}
							
							// childElementMetaList belongs both existingCollectionProperty n incomingCollectionProperty as they similar
							
							
							childSubElementMetaList = new ArrayList<ElementMeta>();
							Map<Integer, Map<Integer, Object>> tempMapForExistingData = new HashMap<Integer, Map<Integer, Object>>();
							Map<Integer, Object> tmp;
							int count = 0;
							
							//send existingCollectionProperty and get meta
							for (Object obj : existingCollectionProperty) {
								
								childElementMeta = new ElementMeta();
								tmp = new HashMap<Integer, Object>();
								
								for (int a=0; a<childObjectFields.size(); a++) {
									
									childObjectFields.get(a).setAccessible(true);
									
									childSubElementMeta = new ElementMeta();
									
									childSubElementMeta.setIsSimilar(false);
									childSubElementMeta.setPropertyName(childObjectFields.get(a).getName());
									childSubElementMeta.setLevel(2);
									childSubElementMeta.setSubElmentMetaList(null);
									
									if (childObjectFields.get(a).get(obj) != null) {
										if (isSimpleDataType(childObjectFields.get(a).get(obj), null)) {
											childSubElementMeta.setIsComplex(false);
											childSubElementMeta.setPropertyValue(childObjectFields.get(a).get(obj).toString());
										} else {
											childSubElementMeta.setIsComplex(true);
											if (Reflect.isCollection(childObjectFields.get(a).get(obj))) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.COLLECTION_DATA_TYPE);
												childSubElementMeta.setPropertyValue("Collection data not described in this level");
											} else if (isOpenMrsObject(childObjectFields.get(a).get(obj), null)) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.OPENMRS_DATA_TYPE);
												childSubElementMeta.setPropertyValue("OpenMRS object not described in this level");
											} else if (isMap(childObjectFields.get(a).get(obj), null)) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.MAP_DATA_TYPE);
												childSubElementMeta.setPropertyValue("Map data not described in this level");
											} else {
												childSubElementMeta.setPropertyValue("Not identified");
											}
										}
									} else {
										childSubElementMeta.setIsComplex(false);
										childSubElementMeta.setPropertyValue(DataComparisonConsts.NULL);
									}
									
									tmp.put(a, childObjectFields.get(a).get(obj));
									childSubElementMetaList.add(childSubElementMeta);
									
								}
								
								childElementMeta.setIsSimilar(false);
								childElementMeta.setIsComplex(true);
								childElementMeta.setLevel(1);
								childElementMeta.setPropertyName("");
								childElementMeta.setSubElmentMetaList(childSubElementMetaList);
								
								existingDifferentChildElementMetaList.add(childElementMeta);
								tempMapForExistingData.put(count, tmp);
								count++;
								
							}
							
							
							//send incomingCollectionProperty and get meta
							childSubElementMetaList = new ArrayList<ElementMeta>();
							
							Map<Integer, Map<Integer, Object>> tempMapForIncomingData = new HashMap<Integer, Map<Integer, Object>>();
							count = 0;
							
							//send incomingCollectionProperty and get meta
							for (Object obj : incomingCollectionProperty) {
								
								childElementMeta = new ElementMeta();
								tmp = new HashMap<Integer, Object>();
								
								for (int a=0; a<childObjectFields.size(); a++) {
									
									childObjectFields.get(a).setAccessible(true);
									
									childSubElementMeta = new ElementMeta();
									
									childSubElementMeta.setIsSimilar(false);
									childSubElementMeta.setPropertyName(childObjectFields.get(a).getName());
									childSubElementMeta.setLevel(2);
									childSubElementMeta.setSubElmentMetaList(null);
									
									if (childObjectFields.get(a).get(obj) != null) {
										if (isSimpleDataType(childObjectFields.get(a).get(obj), null)) {
											childSubElementMeta.setIsComplex(false);
											childSubElementMeta.setPropertyValue(childObjectFields.get(a).get(obj).toString());
										} else {
											childSubElementMeta.setIsComplex(true);
											if (Reflect.isCollection(childObjectFields.get(a).get(obj))) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.COLLECTION_DATA_TYPE);
												childSubElementMeta.setPropertyValue("Collection data not described in this level");
											} else if (isOpenMrsObject(childObjectFields.get(a).get(obj), null)) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.OPENMRS_DATA_TYPE);
												childSubElementMeta.setPropertyValue("OpenMRS object not described in this level");
											} else if (isMap(childObjectFields.get(a).get(obj), null)) {
												childSubElementMeta.setPropertyType(DataComparisonConsts.MAP_DATA_TYPE);
												childSubElementMeta.setPropertyValue("Map data not described in this level");
											} else {
												childSubElementMeta.setPropertyValue("Not identified");
											}
										}
									} else {
										childSubElementMeta.setIsComplex(false);
										childSubElementMeta.setPropertyValue(DataComparisonConsts.NULL);
									}
									
									tmp.put(a, childObjectFields.get(a).get(obj));
									childSubElementMetaList.add(childSubElementMeta);
									
								}
								
								childElementMeta.setIsSimilar(false);
								childElementMeta.setIsComplex(true);
								childElementMeta.setLevel(1);
								childElementMeta.setPropertyName("");
								childElementMeta.setSubElmentMetaList(childSubElementMetaList);
								
								incomingDifferentChildElementMetaList.add(childElementMeta);
								tempMapForIncomingData.put(count, tmp);
								count++;
								
							}
							
							// Compare two maps and set the similarity
							
							int minSize = Math.min(tempMapForExistingData.size(), tempMapForIncomingData.size());
							
							for (int j=0; j<minSize; j++) {
								for (int y=0; y<tempMapForExistingData.get(j).size(); y++) {
									
									if (((tempMapForExistingData.get(j).get(y) == null) && (tempMapForIncomingData.get(j).get(y) == null))
										|| ((tempMapForExistingData.get(j).get(y) != null)
										&& tempMapForExistingData.get(j).get(y).equals(tempMapForIncomingData.get(j).get(y)))
									) {
										existingDifferentChildElementMetaList.get(j).getSubElmentMetaList().get(y).setIsSimilar(true);
										incomingDifferentChildElementMetaList.get(j).getSubElmentMetaList().get(y).setIsSimilar(true);
									}
								}
							}
							
							if ((existingDifferentChildElementMetaList.size() == 0) && (incomingDifferentChildElementMetaList.size() == 0)) {
								existingElementMeta.setIsSimilar(true);
								incomingElementMeta.setIsSimilar(true);
							} else {
								existingElementMeta.setIsSimilar(false);
								incomingElementMeta.setIsSimilar(false);
							}
							
							// Finally add to existingElementMeta
							
							List<ElementMeta> existingSubElements = new ArrayList<ElementMeta>();
							existingSubElements.addAll(childElementMetaList);
							existingSubElements.addAll(existingDifferentChildElementMetaList);
							
							existingElementMeta.setSubElmentMetaList(existingSubElements);
							
							
							List<ElementMeta> incomingSubElements = new ArrayList<ElementMeta>();
							incomingSubElements.addAll(childElementMetaList);
							incomingSubElements.addAll(incomingDifferentChildElementMetaList);
							
							incomingElementMeta.setSubElmentMetaList(incomingSubElements);
							
							metaItems.put("existingItem", existingElementMeta);
							metaItems.put("incomingItem", incomingElementMeta);
							
							rowMeta.setMetaItems(metaItems);
							rowMeta.setIsSimilar(false);
							
						}
						
					}
					
				} else if ((existingCollectionProperty.size() == 0) && (incomingCollectionProperty.size() == 0)) {
					
					existingElementMeta.setIsSimilar(true);
					incomingElementMeta.setIsSimilar(true);
					
					metaItems.put("existingItem", existingElementMeta);
					metaItems.put("incomingItem", incomingElementMeta);
					
					rowMeta.setMetaItems(metaItems);
					rowMeta.setIsSimilar(true);
					
				} else if (existingCollectionProperty.size() > 0) {
					
					
					
				} else if (incomingCollectionProperty.size() > 0) {
					
					
					
				}
				
			} else if (existingElementMeta.getPropertyType() == DataComparisonConsts.MAP_DATA_TYPE) {
				
				// Map data type property
				
				int genericType = -1;
				
				for (Map.Entry<String, ?> entry : ((Map<String, ?>)existingItemPropertyValue).entrySet()) {
					
					if (isSimpleDataType(entry.getValue(), null)) {
						genericType = DataComparisonConsts.SIMPLE_DATA_TYPE;
					} else if (Reflect.isCollection(entry.getValue())) {
						genericType = DataComparisonConsts.COLLECTION_DATA_TYPE;
					} else if (isMap(entry.getValue(), null)) {
						genericType = DataComparisonConsts.MAP_DATA_TYPE;
					} else if (isOpenMrsObject(entry.getValue(), null)) {
						genericType = DataComparisonConsts.OPENMRS_DATA_TYPE;
					}
					
					break;
					
				}
				
				// If Map properties are similar
				if (existingItemPropertyValue.equals(incomingItemPropertyValue)) {
					
					if (genericType == DataComparisonConsts.SIMPLE_DATA_TYPE) {
						
						existingSubElmentMetaList = new ArrayList<ElementMeta>();
						
						for (Map.Entry<String, ?> entry : ((Map<String, ?>)existingItemPropertyValue).entrySet()) {
							
							ElementMeta em = new ElementMeta();
							
							em.setIsComplex(false);
							em.setLevel(2);
							em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
							em.setPropertyName(entry.getKey());
							em.setPropertyValue(entry.getValue().toString());
							em.setSubElmentMetaList(null);
							em.setIsSimilar(true);
							
							existingSubElmentMetaList.add(em);
							
						}
						
						// Since both are same
						existingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
						incomingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
						
						metaItems.put("existingItem", existingElementMeta);
						metaItems.put("incomingItem", incomingElementMeta);
						
						rowMeta.setMetaItems(metaItems);
						rowMeta.setIsSimilar(true);
						
					} else if (genericType == DataComparisonConsts.COLLECTION_DATA_TYPE) {
						
						existingElementMeta.setPropertyValue("Collection type data values not described in this level");
						incomingElementMeta.setPropertyValue("Collection type data values not described in this level");
						
						metaItems.put("existingItem", existingElementMeta);
						metaItems.put("incomingItem", incomingElementMeta);
						
						rowMeta.setMetaItems(metaItems);
						rowMeta.setIsSimilar(true);
						
					} else if (genericType == DataComparisonConsts.MAP_DATA_TYPE) {
						
						existingElementMeta.setPropertyValue("Map type data values not described in this level");
						incomingElementMeta.setPropertyValue("Map type data values not described in this level");
						
						metaItems.put("existingItem", existingElementMeta);
						metaItems.put("incomingItem", incomingElementMeta);
						
						rowMeta.setMetaItems(metaItems);
						rowMeta.setIsSimilar(true);
						
					} else if (genericType == DataComparisonConsts.OPENMRS_DATA_TYPE) {
						
						existingElementMeta.setPropertyValue("OpenMRS data type values not described in this level");
						incomingElementMeta.setPropertyValue("OpenMRS data type values not described in this level");
						
						metaItems.put("existingItem", existingElementMeta);
						metaItems.put("incomingItem", incomingElementMeta);
						
						rowMeta.setMetaItems(metaItems);
						rowMeta.setIsSimilar(true);
						
					}
					
				} else {
					
					// Not similar map type properties
					
					if (genericType == DataComparisonConsts.SIMPLE_DATA_TYPE) {
						
						existingSubElmentMetaList = new ArrayList<ElementMeta>();
						incomingSubElmentMetaList = new ArrayList<ElementMeta>();
						
						Set<Entry> similarEntrySet = new HashSet<Entry>();
						Set<Entry> existingDifferEntrySet = new HashSet<Entry>();
						Set<Entry> incomingDifferEntrySet = new HashSet<Entry>();
						
						List<ElementMeta> similarList = new ArrayList<ElementMeta>();
						List<ElementMeta> existingDifferList = new ArrayList<ElementMeta>();
						List<ElementMeta> incomingDifferList = new ArrayList<ElementMeta>();
						
						Map<String, ?> existingMap = (Map<String, ?>)existingItemPropertyValue;
						Map<String, ?> incomingMap = (Map<String, ?>)incomingItemPropertyValue;
						
						if (existingMap.size() <= incomingMap.size()) {
							
							Set<?> incomingMapEntrySet = incomingMap.entrySet();
							
							for (Map.Entry<String, ?> entry : ((Map<String, ?>)existingItemPropertyValue).entrySet()) {
								
								ElementMeta em = new ElementMeta();
								
								em.setIsComplex(false);
								em.setLevel(2);
								em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
								em.setPropertyName(entry.getKey());
								em.setPropertyValue(entry.getValue().toString());
								em.setSubElmentMetaList(null);
								
								if (incomingMapEntrySet.contains(entry)) {
									
									similarEntrySet.add(entry);
									
									em.setIsSimilar(true);
									similarList.add(em);
								} else {
									
									existingDifferEntrySet.add(entry);
									
									em.setIsSimilar(false);
									existingDifferList.add(em);
								}
								
							}
							
							incomingMapEntrySet.removeAll(similarEntrySet);
							incomingDifferEntrySet.addAll((Collection<? extends Entry>) incomingMapEntrySet);
							
							for (Object entry : incomingDifferEntrySet) {
								
								ElementMeta em = new ElementMeta();
								
								em.setIsComplex(false);
								em.setLevel(2);
								em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
								em.setPropertyName(((Entry<String, ?>)entry).getKey());
								em.setPropertyValue(((Entry<String, ?>)entry).getValue().toString());
								em.setSubElmentMetaList(null);
								em.setIsSimilar(false);
								
								incomingDifferList.add(em);
								
							}
							
						} else {
							
							Set<?> existingMapEntrySet = existingMap.entrySet();
							
							for (Map.Entry<String, ?> entry : ((Map<String, ?>)incomingItemPropertyValue).entrySet()) {
								
								ElementMeta em = new ElementMeta();
								
								em.setIsComplex(false);
								em.setLevel(2);
								em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
								em.setPropertyName(entry.getKey());
								em.setPropertyValue(entry.getValue().toString());
								em.setSubElmentMetaList(null);
								
								if (existingMapEntrySet.contains(entry)) {
									
									similarEntrySet.add(entry);
									
									em.setIsSimilar(true);
									similarList.add(em);
								} else {
									
									incomingDifferEntrySet.add(entry);
									
									em.setIsSimilar(false);
									incomingDifferList.add(em);
								}
								
							}
							
							existingMapEntrySet.removeAll(similarEntrySet);
							existingDifferEntrySet.addAll((Collection<? extends Entry>) existingMapEntrySet);
							
							for (Object entry : existingDifferEntrySet) {
								
								ElementMeta em = new ElementMeta();
								
								em.setIsComplex(false);
								em.setLevel(2);
								em.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
								em.setPropertyName(((Entry<String, ?>)entry).getKey());
								em.setPropertyValue(((Entry<String, ?>)entry).getValue().toString());
								em.setSubElmentMetaList(null);
								em.setIsSimilar(false);
								
								existingDifferList.add(em);
								
							}
							
						}
						
						existingSubElmentMetaList.addAll(similarList);
						existingSubElmentMetaList.addAll(existingDifferList);
						
						incomingSubElmentMetaList.addAll(similarList);
						incomingSubElmentMetaList.addAll(incomingDifferList);
						
						// Since both are same
						existingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
						incomingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
						
						metaItems.put("existingItem", existingElementMeta);
						metaItems.put("incomingItem", incomingElementMeta);
						
						rowMeta.setMetaItems(metaItems);
						rowMeta.setIsSimilar(false);
						
					} else if (genericType == DataComparisonConsts.COLLECTION_DATA_TYPE) {
						
						existingElementMeta.setPropertyValue("Collection type data values not described in this level");
						incomingElementMeta.setPropertyValue("Collection type data values not described in this level");
						
						metaItems.put("existingItem", existingElementMeta);
						metaItems.put("incomingItem", incomingElementMeta);
						
						rowMeta.setMetaItems(metaItems);
						rowMeta.setIsSimilar(false);
						
					} else if (genericType == DataComparisonConsts.MAP_DATA_TYPE) {
						
						existingElementMeta.setPropertyValue("Map type data values not described in this level");
						incomingElementMeta.setPropertyValue("Map type data values not described in this level");
						
						metaItems.put("existingItem", existingElementMeta);
						metaItems.put("incomingItem", incomingElementMeta);
						
						rowMeta.setMetaItems(metaItems);
						rowMeta.setIsSimilar(false);
						
					} else if (genericType == DataComparisonConsts.OPENMRS_DATA_TYPE) {
						
						existingElementMeta.setPropertyValue("OpenMRS data type values not described in this level");
						incomingElementMeta.setPropertyValue("OpenMRS data type values not described in this level");
						
						metaItems.put("existingItem", existingElementMeta);
						metaItems.put("incomingItem", incomingElementMeta);
						
						rowMeta.setMetaItems(metaItems);
						rowMeta.setIsSimilar(false);
						
					}
					
				}
				
				
			} else if (existingElementMeta.getPropertyType() == DataComparisonConsts.OPENMRS_DATA_TYPE) {
				
				// OpenMRS object type property
				
				Class childClass = incomingItemPropertyValue.getClass();
				List<Field> childClassFields = Reflect.getAllFields(childClass);
				ElementMeta existingChildElementMeta;
				ElementMeta incomingChildElementMeta;
				Collection<?> existingChildCollectionProperty;
				Collection<?> incomingChildCollectionProperty;
				
				if (existingItemPropertyValue.equals(incomingItemPropertyValue)) {
					rowMeta.setIsSimilar(true);
				} else {
					rowMeta.setIsSimilar(false);
				}
				
				existingSubElmentMetaList = new ArrayList<ElementMeta>();
				incomingSubElmentMetaList = new ArrayList<ElementMeta>();
				
				for (int i=0; i<childClassFields.size(); i++) {
					
					childClassFields.get(i).setAccessible(true);
					
					Object existingChildFieldValue = childClassFields.get(i).get(existingItemPropertyValue);
					Object incomingChildFieldValue = childClassFields.get(i).get(incomingItemPropertyValue);
					
					existingChildElementMeta = new ElementMeta();
					existingChildElementMeta.setPropertyName(childClassFields.get(i).getName());
					
					if (existingChildFieldValue != null) {
						
						existingChildElementMeta = getElementMetaObjectAfterSetProperties(existingChildElementMeta, existingChildFieldValue);
		                
		            } else {
		            	existingChildElementMeta.setIsComplex(false);
		            	existingChildElementMeta.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
		            	existingChildElementMeta.setPropertyValue(DataComparisonConsts.NULL);
		            }
					
					incomingChildElementMeta = new ElementMeta();
					incomingChildElementMeta.setPropertyName(childClassFields.get(i).getName());
					
					if (incomingChildFieldValue != null) {
						
						incomingChildElementMeta = getElementMetaObjectAfterSetProperties(incomingChildElementMeta, incomingChildFieldValue);
						
					} else {
						incomingChildElementMeta.setIsComplex(false);
						incomingChildElementMeta.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
						incomingChildElementMeta.setPropertyValue(DataComparisonConsts.NULL);
					}
					
					if ((existingChildFieldValue != null) && (incomingChildFieldValue != null)) {
						if (existingChildFieldValue.equals(incomingChildFieldValue)) {
							existingChildElementMeta.setIsSimilar(true);
							incomingChildElementMeta.setIsSimilar(true);
		                } else {
		                	existingChildElementMeta.setIsSimilar(false);
		                	incomingChildElementMeta.setIsSimilar(false);
		                }
					} else if ((existingChildFieldValue == null) && (incomingChildFieldValue == null)) {
						existingChildElementMeta.setIsSimilar(true);
						incomingChildElementMeta.setIsSimilar(true);
					} else {
						existingChildElementMeta.setIsSimilar(false);
						incomingChildElementMeta.setIsSimilar(false);
					}
					
					if (existingChildElementMeta.getIsComplex() || incomingChildElementMeta.getIsComplex()) {
						
						if (((existingChildFieldValue != null) && (incomingChildFieldValue != null))
							&& (existingElementMeta.getPropertyType() == DataComparisonConsts.COLLECTION_DATA_TYPE)
						) {
							
							existingChildCollectionProperty = (Collection<?>) existingItemPropertyValue;
							incomingChildCollectionProperty = (Collection<?>) incomingItemPropertyValue;
							
							if (existingChildCollectionProperty.containsAll(incomingChildCollectionProperty)
								&& incomingChildCollectionProperty.containsAll(existingChildCollectionProperty)
							) {
								existingChildElementMeta.setIsSimilar(true);
								incomingChildElementMeta.setIsSimilar(true);
							} else {
								existingChildElementMeta.setIsSimilar(false);
								incomingChildElementMeta.setIsSimilar(false);
							}
							
						} else if (((existingChildFieldValue != null) && (incomingChildFieldValue != null))
							&& (existingElementMeta.getPropertyType() == DataComparisonConsts.OPENMRS_DATA_TYPE)
						) {
							
							if (existingChildFieldValue.equals(incomingChildFieldValue)) {
								existingChildElementMeta.setIsSimilar(true);
								incomingChildElementMeta.setIsSimilar(true);
							} else {
								existingChildElementMeta.setIsSimilar(false);
								incomingChildElementMeta.setIsSimilar(false);
							}
							
						} else if (((existingChildFieldValue != null) && (incomingChildFieldValue != null))
							&& (existingElementMeta.getPropertyType() == DataComparisonConsts.MAP_DATA_TYPE)
						) {
							
							if (existingChildFieldValue.equals(incomingChildFieldValue)) {
								existingChildElementMeta.setIsSimilar(true);
								incomingChildElementMeta.setIsSimilar(true);
							} else {
								existingChildElementMeta.setIsSimilar(false);
								incomingChildElementMeta.setIsSimilar(false);
							}
							
						}
						
					}
					
					existingSubElmentMetaList.add(existingChildElementMeta);
					incomingSubElmentMetaList.add(incomingChildElementMeta);
					
				}
				
				existingElementMeta.setSubElmentMetaList(existingSubElmentMetaList);
				incomingElementMeta.setSubElmentMetaList(incomingSubElmentMetaList);
				
				metaItems.put("existingItem", existingElementMeta);
				metaItems.put("incomingItem", incomingElementMeta);
				rowMeta.setMetaItems(metaItems);
				
			}
			
		} else { // One property value can be null
			
		}
		
		return rowMeta;
		
	}
	
	/**
	 * Set the ElementMeta properties using the relevant metadata object property,
	 * and return ElementMeta object.
	 * 
	 * @param elementMetaItem the meta data for the considering object property
	 * @param data the considering property value
	 * @return ElementMeta 
	 */
	private ElementMeta getElementMetaObjectAfterSetProperties(ElementMeta elementMetaItem, Object data) {
		
		if (isSimpleDataType(data, null)) {
        	
			elementMetaItem.setIsComplex(false);
			elementMetaItem.setPropertyType(DataComparisonConsts.SIMPLE_DATA_TYPE);
			elementMetaItem.setPropertyValue(data.toString());
        	
        } else if (Reflect.isCollection(data)) {
        	
        	elementMetaItem.setPropertyType(DataComparisonConsts.COLLECTION_DATA_TYPE);
        	elementMetaItem.setPropertyValue("");
        	elementMetaItem.setIsComplex(true);
        	
        } else if (isMap(data, null)) {
        	
        	// Dummy code for testing
        	elementMetaItem.setPropertyType(DataComparisonConsts.MAP_DATA_TYPE);
        	elementMetaItem.setPropertyValue("Map Data Type");
        	elementMetaItem.setIsComplex(true);
        	
        } else if (isOpenMrsObject(data, null)) {
        	
        	elementMetaItem.setPropertyType(DataComparisonConsts.OPENMRS_DATA_TYPE);
        	elementMetaItem.setPropertyValue("");
        	elementMetaItem.setIsComplex(true);
        	
        } else {
        	
        	// Dummy code for testing
        	elementMetaItem.setPropertyType(3);
        	elementMetaItem.setPropertyValue("Undefined Property");
        	elementMetaItem.setIsComplex(false);
        	
        }
		
		return elementMetaItem;
		
	}
	
	/**
	 * Check whether the given object property value
	 * can be directly accessed or not.
	 * 
	 * @param data the object property value
	 * @return boolean
	 */
	private boolean isSimpleDataType(Object data, Class c) {
		
		Class clazz;
		
		if (data != null) {
			clazz = data.getClass();
		} else if (c != null) {
			clazz = c;
		} else {
			return false;
		}
		
		return clazz.isPrimitive() ||
			clazz == java.lang.Long.class ||
			clazz == java.lang.String.class ||
			clazz == java.lang.Integer.class ||
			clazz == java.lang.Boolean.class ||
			clazz == java.lang.Float.class ||
			clazz == java.lang.Double.class ||
			clazz == java.lang.Byte.class ||
			clazz == java.lang.Character.class ||
			clazz == java.lang.Short.class ||
			clazz == java.util.Date.class ||
			clazz == java.sql.Timestamp.class;
		
	}
	
	/**
	 * Check whether the object is OpenMRS DTO.
	 * 
	 * @param data the Object to check
	 * @return boolean
	 */
	private boolean isOpenMrsObject(Object data, Class c) {
		
		Class clazz;
		
		if (data != null) {
			clazz = data.getClass();
		} else if (c != null) {
			clazz = c;
		} else {
			return false;
		}
		
		return OpenmrsObject.class.isAssignableFrom(clazz);
		
	}
	
	/**
	 * Check whether the object is Map.
	 * 
	 * @param data the Object to check
	 * @return boolean
	 */
	private boolean isMap(Object data, Class c) {
		
		Class clazz;
		
		if (data != null) {
			clazz = data.getClass();
		} else if (c != null) {
			clazz = c;
		} else {
			return false;
		}
		
		return Map.class.isAssignableFrom(clazz);
		
	}
	
}
