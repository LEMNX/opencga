/*
 * Copyright 2015-2017 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.catalog.managers;

import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.AnnotationSetDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.core.models.Annotation;
import org.opencb.opencga.core.models.AnnotationSet;
import org.opencb.opencga.core.models.VariableSet;
import org.opencb.opencga.catalog.utils.CatalogAnnotationsValidator;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.config.Configuration;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Created by pfurio on 06/07/16.
 */
public abstract class AnnotationSetManager<R> extends ResourceManager<R> {

    AnnotationSetManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                         DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory, Configuration configuration) {
        super(authorizationManager, auditManager, catalogManager, catalogDBAdaptorFactory, ioManagerFactory, configuration);
    }

    /**
     * General method to create an annotation set that will have to be implemented. The managers implementing it will have to check the
     * validity of the sessionId and permissions and call the general createAnnotationSet implemented above.
     *
     * @param id id of the entity being annotated.
     * @param studyStr study string.
     * @param variableSetId variable set id or name under which the annotation will be made.
     * @param annotationSetName annotation set name that will be used for the annotation.
     * @param annotations map of annotations to create the annotation set.
     * @param attributes map with further attributes that the user might be interested in storing.
     * @param sessionId session id of the user asking for the operation.
     * @return a queryResult object with the annotation set created.
     * @throws CatalogException when the session id is not valid, the user does not have permissions or any of the annotation
     * parameters are not valid.
     */
    public abstract QueryResult<AnnotationSet> createAnnotationSet(String id, @Nullable String studyStr, String variableSetId,
                                                            String annotationSetName, Map<String, Object> annotations,
                                                            Map<String, Object> attributes, String sessionId) throws CatalogException;

    /**
     * Retrieve all the annotation sets corresponding to entity.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param sessionId session id of the user asking for the annotation.
     * @return a queryResult containing all the annotation sets for that entity.
     * @throws CatalogException when the session id is not valid or the user does not have proper permissions to see the annotations.
     */
    public abstract QueryResult<AnnotationSet> getAllAnnotationSets(String id, @Nullable String studyStr, String sessionId)
            throws CatalogException;

    /**
     * Retrieve all the annotation sets corresponding to entity.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param sessionId session id of the user asking for the annotation.
     * @return a queryResult containing all the annotation sets for that entity as key:value pairs.
     * @throws CatalogException when the session id is not valid or the user does not have proper permissions to see the annotations.
     */
    public abstract QueryResult<ObjectMap> getAllAnnotationSetsAsMap(String id, @Nullable String studyStr, String sessionId)
            throws CatalogException;

    /**
     * Retrieve the annotation set of the corresponding entity.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param annotationSetName annotation set name of the annotation that will be returned.
     * @param sessionId session id of the user asking for the annotation.
     * @return a queryResult containing the annotation set for that entity.
     * @throws CatalogException when the session id is not valid, the user does not have proper permissions to see the annotations or the
     * annotationSetName is not valid.
     */
    public abstract QueryResult<AnnotationSet> getAnnotationSet(String id, @Nullable String studyStr, String annotationSetName,
                                                                String sessionId) throws CatalogException;

    /**
     * Retrieve the annotation set of the corresponding entity.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param annotationSetName annotation set name of the annotation that will be returned.
     * @param sessionId session id of the user asking for the annotation.
     * @return a queryResult containing the annotation set for that entity as key:value pairs.
     * @throws CatalogException when the session id is not valid, the user does not have proper permissions to see the annotations or the
     * annotationSetName is not valid.
     */
    public abstract QueryResult<ObjectMap> getAnnotationSetAsMap(String id, @Nullable String studyStr, String annotationSetName,
                                                                 String sessionId) throws CatalogException;


    /**
     * Update the values of the annotation set.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param annotationSetName annotation set name of the annotation that will be returned.
     * @param newAnnotations map with the annotations that will have to be changed with the new values.
     * @param sessionId session id of the user asking for the annotation.
     * @return a queryResult object containing the annotation set after the update.
     * @throws CatalogException when the session id is not valid, the user does not have permissions to update the annotationSet,
     * the newAnnotations are not correct or the annotationSetName is not valid.
     */
    public abstract QueryResult<AnnotationSet> updateAnnotationSet(String id, @Nullable String studyStr,  String annotationSetName,
                                                   Map<String, Object> newAnnotations, String sessionId) throws CatalogException;

    /**
     * Deletes the annotation set.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param annotationSetName annotation set name of the annotation to be deleted.
     * @param sessionId session id of the user asking for the annotation.
     * @return a queryResult object with the annotationSet that has been deleted.
     * @throws CatalogException when the session id is not valid, the user does not have permissions to delete the annotationSet or
     * the annotation set name is not valid.
     */
    public abstract QueryResult<AnnotationSet> deleteAnnotationSet(String id, @Nullable String studyStr, String annotationSetName,
                                                            String sessionId) throws CatalogException;

    /**
     * Deletes (or puts to the default value if mandatory) a list of annotations from the annotation set.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param annotationSetName annotation set name of the annotation where the update will be made.
     * @param annotations comma separated list of annotation names that will be deleted or updated to the default values.
     * @param sessionId session id of the user asking for the annotation.
     * @return a queryResult object with the annotation set after applying the changes.
     * @throws CatalogException when the session id is not valid, the user does not have permissions to delete the annotationSet,
     * the annotation set name is not valid or any of the annotation names are not valid.
     */
    public QueryResult<AnnotationSet> deleteAnnotations(String id, @Nullable String studyStr, String annotationSetName, String annotations,
                                                         String sessionId) throws CatalogException {
        throw new CatalogException("Operation still not implemented");
    }

    /**
     * Searches for annotation sets matching the parameters.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param variableSetId variable set id or name.
     * @param annotation comma separated list of annotations by which to look for the annotationSets.
     * @param sessionId session id of the user asking for the annotationSets
     * @return a queryResult object containing the list of annotation sets that matches the query as key:value pairs.
     * @throws CatalogException when the session id is not valid, the user does not have permissions to look for annotationSets.
     */
    public abstract QueryResult<ObjectMap> searchAnnotationSetAsMap(String id, @Nullable String studyStr, String variableSetId,
                                                             @Nullable String annotation, String sessionId) throws CatalogException;

    /**
     * Searches for annotation sets matching the parameters.
     *
     * @param id id of the entity storing the annotation.
     * @param studyStr study string.
     * @param variableSetId variable set id or name.
     * @param annotation comma separated list of annotations by which to look for the annotationSets.
     * @param sessionId session id of the user asking for the annotationSets
     * @return a queryResult object containing the list of annotation sets that matches the query.
     * @throws CatalogException when the session id is not valid, the user does not have permissions to look for annotationSets.
     */
    public abstract QueryResult<AnnotationSet> searchAnnotationSet(String id, @Nullable String studyStr, String variableSetId,
                                                            @Nullable String annotation, String sessionId) throws CatalogException;

    protected List<AnnotationSet> validateAnnotationSets(List<AnnotationSet> annotationSetList) throws CatalogException {
        List<AnnotationSet> retAnnotationSetList = new ArrayList<>(annotationSetList.size());

        Iterator<AnnotationSet> iterator = annotationSetList.iterator();
        while (iterator.hasNext()) {
            AnnotationSet originalAnnotSet = iterator.next();
            String annotationSetName = originalAnnotSet.getName();
            ParamUtils.checkAlias(annotationSetName, "annotationSetName", -1);

            // Get the variable set
            VariableSet variableSet = studyDBAdaptor.getVariableSet(originalAnnotSet.getVariableSetId(), QueryOptions.empty()).first();

            // All the annotationSets the object had in order to check for duplicities assuming all annotationsets have been provided
            List<AnnotationSet> annotationSets = retAnnotationSetList;

            // Check validity of annotations and duplicities
            CatalogAnnotationsValidator.checkAnnotationSet(variableSet, originalAnnotSet, annotationSets);

            // Add the annotation to the list of annotations
            retAnnotationSetList.add(originalAnnotSet);
        }

        return retAnnotationSetList;
    }

    /**
     * Creates an annotation set for the selected entity.
     *
     * @param id id of the entity being annotated.
     * @param variableSet variable set under which the annotation will be made.
     * @param annotationSetName annotation set name that will be used for the annotation.
     * @param annotations map of annotations to create the annotation set.
     * @param release Current project release.
     * @param attributes map with further attributes that the user might be interested in storing.
     * @param dbAdaptor DB Adaptor to make the correspondent call to create the annotation set.
     * @return a queryResult object with the annotation set created.
     * @throws CatalogException if the annotation is not valid.
     */
    protected QueryResult<AnnotationSet> createAnnotationSet(long id, VariableSet variableSet, String annotationSetName,
                                                                 Map<String, Object> annotations, int release,
                                                                 Map<String, Object> attributes, AnnotationSetDBAdaptor dbAdaptor)
            throws CatalogException {

        ParamUtils.checkAlias(annotationSetName, "annotationSetName", -1);

        // Create empty annotation set
        AnnotationSet annotationSet = new AnnotationSet(annotationSetName, variableSet.getId(), new HashSet<>(), TimeUtils.getTime(),
                release, attributes);

        // Fill the annotation set object with the annotations
        for (Map.Entry<String, Object> entry : annotations.entrySet()) {
            annotationSet.getAnnotations().add(new Annotation(entry.getKey(), entry.getValue()));
        }

        // Obtain all the annotationSets the object had in order to check for duplicities
        QueryResult<AnnotationSet> annotationSetQueryResult = dbAdaptor.getAnnotationSet(id, null);
        List<AnnotationSet> annotationSets;
        if (annotationSetQueryResult == null || annotationSetQueryResult.getNumResults() == 0) {
            annotationSets = Collections.emptyList();
        } else {
            annotationSets = annotationSetQueryResult.getResult();
        }

        // Check validity of annotations and duplicities
        CatalogAnnotationsValidator.checkAnnotationSet(variableSet, annotationSet, annotationSets);

        // Register the annotation set in the database
        return dbAdaptor.createAnnotationSet(id, annotationSet);
    }

    /**
     * Update the annotation set.
     *
     * @param resource resource of the entity where the annotation set will be updated.
     * @param annotationSetName annotation set name of the annotation to be updated.
     * @param newAnnotations map with the annotations that will have to be changed with the new values.
     * @param dbAdaptor DBAdaptor of the entity corresponding to the id.
     * @return a queryResult containing the annotation set after the update.
     * @throws CatalogException when the annotation set name could not be found or the new annotation is not valid.
     */
    protected QueryResult<AnnotationSet> updateAnnotationSet(MyResourceId resource, String annotationSetName,
                                                             Map<String, Object> newAnnotations, AnnotationSetDBAdaptor dbAdaptor)
            throws CatalogException {
        if (newAnnotations == null) {
            throw new CatalogException("Missing annotations to be updated");
        }
        // Obtain the annotation set to be updated
        QueryResult<AnnotationSet> queryResult = dbAdaptor.getAnnotationSet(resource.getResourceId(), annotationSetName);
        if (queryResult == null || queryResult.getNumResults() == 0) {
            throw new CatalogException("No annotation could be found under the name " + annotationSetName);
        }
        AnnotationSet annotationSet = queryResult.first();

        // Get the variableSet
        QueryResult<VariableSet> variableSetQR = studyDBAdaptor.getVariableSet(annotationSet.getVariableSetId(), null, resource.getUser(),
                null);
        if (variableSetQR.getNumResults() == 0) {
            // Variable set must be confidential and the user does not have those permissions
            throw new CatalogAuthorizationException("Permission denied: User " + resource.getUser() + " cannot create annotations over "
                    + "that variable set");
        }

        // Update and validate annotations
        CatalogAnnotationsValidator.mergeNewAnnotations(annotationSet, newAnnotations);
        CatalogAnnotationsValidator.checkAnnotationSet(variableSetQR.first(), annotationSet, null);

        // Update the annotation set in the database
        return dbAdaptor.updateAnnotationSet(resource.getResourceId(), annotationSet);
    }

}
