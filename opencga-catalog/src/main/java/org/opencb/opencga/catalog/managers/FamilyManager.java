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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.audit.AuditRecord;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.DBIterator;
import org.opencb.opencga.catalog.db.api.FamilyDBAdaptor;
import org.opencb.opencga.catalog.db.api.IndividualDBAdaptor;
import org.opencb.opencga.catalog.db.mongodb.MongoDBAdaptorFactory;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.models.*;
import org.opencb.opencga.core.models.acls.AclParams;
import org.opencb.opencga.core.models.acls.permissions.FamilyAclEntry;
import org.opencb.opencga.core.models.acls.permissions.StudyAclEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.opencb.opencga.catalog.auth.authorization.CatalogAuthorizationManager.checkPermissions;

/**
 * Created by pfurio on 02/05/17.
 */
public class FamilyManager extends AnnotationSetManager<Family> {

    protected static Logger logger = LoggerFactory.getLogger(FamilyManager.class);
    private UserManager userManager;
    private StudyManager studyManager;

    FamilyManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                         DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory, Configuration configuration) {
        super(authorizationManager, auditManager, catalogManager, catalogDBAdaptorFactory, ioManagerFactory, configuration);

        this.userManager = catalogManager.getUserManager();
        this.studyManager = catalogManager.getStudyManager();
    }

    /**
     * Obtains the resource java bean containing the requested ids.
     *
     * @param familyStr Family id in string format. Could be either the id or name.
     * @param studyStr Study id in string format. Could be one of [id|user@aliasProject:aliasStudy|aliasProject:aliasStudy|aliasStudy].
     * @param sessionId Session id of the user logged.
     * @return the resource java bean containing the requested ids.
     * @throws CatalogException when more than one family id is found.
     */
    public MyResourceId getId(String familyStr, @Nullable String studyStr, String sessionId) throws CatalogException {
        if (StringUtils.isEmpty(familyStr)) {
            throw new CatalogException("Missing family parameter");
        }

        String userId;
        long studyId;
        long familyId;

        if (StringUtils.isNumeric(familyStr) && Long.parseLong(familyStr) > configuration.getCatalog().getOffset()) {
            familyId = Long.parseLong(familyStr);
            familyDBAdaptor.exists(familyId);
            studyId = familyDBAdaptor.getStudyId(familyId);
            userId = catalogManager.getUserManager().getUserId(sessionId);
        } else {
            if (familyStr.contains(",")) {
                throw new CatalogException("More than one family found");
            }

            userId = catalogManager.getUserManager().getUserId(sessionId);
            studyId = catalogManager.getStudyManager().getId(userId, studyStr);

            Query query = new Query()
                    .append(FamilyDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(FamilyDBAdaptor.QueryParams.NAME.key(), familyStr);
            QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, FamilyDBAdaptor.QueryParams.ID.key());
            QueryResult<Family> familyQueryResult = familyDBAdaptor.get(query, queryOptions);
            if (familyQueryResult.getNumResults() == 1) {
                familyId = familyQueryResult.first().getId();
            } else {
                if (familyQueryResult.getNumResults() == 0) {
                    throw new CatalogException("Family " + familyStr + " not found in study " + studyStr);
                } else {
                    throw new CatalogException("More than one family found under " + familyStr + " in study " + studyStr);
                }
            }
        }

        return new MyResourceId(userId, studyId, familyId);
    }

    /**
     * Obtains the resource java bean containing the requested ids.
     *
     * @param familyStr Family id in string format. Could be either the id or name.
     * @param studyStr Study id in string format. Could be one of [id|user@aliasProject:aliasStudy|aliasProject:aliasStudy|aliasStudy].
     * @param sessionId Session id of the user logged.
     * @return the resource java bean containing the requested ids.
     * @throws CatalogException CatalogException.
     */
    public MyResourceIds getIds(String familyStr, @Nullable String studyStr, String sessionId) throws CatalogException {
        if (StringUtils.isEmpty(familyStr)) {
            throw new CatalogException("Missing family parameter");
        }

        String userId;
        long studyId;
        List<Long> familyIds = new ArrayList<>();

        if (StringUtils.isNumeric(familyStr) && Long.parseLong(familyStr) > configuration.getCatalog().getOffset()) {
            familyIds = Arrays.asList(Long.parseLong(familyStr));
            familyDBAdaptor.checkId(familyIds.get(0));
            studyId = familyDBAdaptor.getStudyId(familyIds.get(0));
            userId = catalogManager.getUserManager().getUserId(sessionId);
        } else {
            userId = catalogManager.getUserManager().getUserId(sessionId);
            studyId = catalogManager.getStudyManager().getId(userId, studyStr);

            List<String> familySplit = Arrays.asList(familyStr.split(","));
            for (String familyStrAux : familySplit) {
                if (StringUtils.isNumeric(familyStrAux)) {
                    long familyId = Long.parseLong(familyStrAux);
                    familyDBAdaptor.exists(familyId);
                    familyIds.add(familyId);
                }
            }

            Query query = new Query()
                    .append(FamilyDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(FamilyDBAdaptor.QueryParams.NAME.key(), familySplit);
            QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, FamilyDBAdaptor.QueryParams.ID.key());
            QueryResult<Family> familyQueryResult = familyDBAdaptor.get(query, queryOptions);

            if (familyQueryResult.getNumResults() > 0) {
                familyIds.addAll(familyQueryResult.getResult().stream().map(Family::getId).collect(Collectors.toList()));
            }

            if (familyIds.size() < familySplit.size()) {
                throw new CatalogException("Found only " + familyIds.size() + " out of the " + familySplit.size()
                        + " families looked for in study " + studyStr);
            }
        }

        return new MyResourceIds(userId, studyId, familyIds);
    }

    @Override
    public Long getStudyId(long entryId) throws CatalogException {
        return familyDBAdaptor.getStudyId(entryId);
    }

    @Override
    public DBIterator<Family> iterator(String studyStr, Query query, QueryOptions options, String sessionId) throws CatalogException {
        return null;
    }

    public QueryResult<Family> create(String studyStr, Family family, QueryOptions options, String sessionId) throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        long studyId = catalogManager.getStudyManager().getId(userId, studyStr);
        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.WRITE_FAMILIES);

        ParamUtils.checkObj(family, "family");
        ParamUtils.checkAlias(family.getName(), "name", configuration.getCatalog().getOffset());
        family.setCreationDate(TimeUtils.getTime());
        family.setDescription(ParamUtils.defaultString(family.getDescription(), ""));
        family.setStatus(new Status());
        family.setAnnotationSets(ParamUtils.defaultObject(family.getAnnotationSets(), Collections.emptyList()));
        family.setAnnotationSets(validateAnnotationSets(family.getAnnotationSets()));
        family.setRelease(catalogManager.getStudyManager().getCurrentRelease(studyId));
        family.setAttributes(ParamUtils.defaultObject(family.getAttributes(), Collections.emptyMap()));

        checkAndCreateAllIndividualsFromFamily(studyId, family, sessionId);

        options = ParamUtils.defaultObject(options, QueryOptions::new);
        QueryResult<Family> queryResult = familyDBAdaptor.insert(family, studyId, options);
        auditManager.recordCreation(AuditRecord.Resource.family, queryResult.first().getId(), userId, queryResult.first(), null, null);

        addMemberInformation(queryResult, studyId, sessionId);
        return queryResult;
    }

    @Override
    public QueryResult<Family> get(String studyStr, Query query, QueryOptions options, String sessionId) throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getUserId(sessionId);
        long studyId = studyManager.getId(userId, studyStr);

        query.append(FamilyDBAdaptor.QueryParams.STUDY_ID.key(), studyId);

        QueryResult<Family> familyQueryResult = familyDBAdaptor.get(query, options, userId);
        addMemberInformation(familyQueryResult, studyId, sessionId);

        return familyQueryResult;
    }

    public QueryResult<Family> search(String studyStr, Query query, QueryOptions options, String sessionId) throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        long studyId = catalogManager.getStudyManager().getId(userId, studyStr);

        // The individuals introduced could be either ids or names. As so, we should use the smart resolutor to do this.
        // We change the FATHER, MOTHER and MEMBER parameters for FATHER_ID, MOTHER_ID and MEMBER_ID which is what the DBAdaptor
        // understands
        if (StringUtils.isNotEmpty(query.getString(FamilyDBAdaptor.QueryParams.FATHER.key()))) {
            MyResourceIds resourceIds = catalogManager.getIndividualManager()
                    .getIds(query.getString(FamilyDBAdaptor.QueryParams.FATHER.key()), Long.toString(studyId), sessionId);
            query.put(FamilyDBAdaptor.QueryParams.FATHER_ID.key(), resourceIds.getResourceIds());
            query.remove(FamilyDBAdaptor.QueryParams.FATHER.key());
        }
        if (StringUtils.isNotEmpty(query.getString(FamilyDBAdaptor.QueryParams.MOTHER.key()))) {
            MyResourceIds resourceIds = catalogManager.getIndividualManager()
                    .getIds(query.getString(FamilyDBAdaptor.QueryParams.MOTHER.key()), Long.toString(studyId), sessionId);
            query.put(FamilyDBAdaptor.QueryParams.MOTHER_ID.key(), resourceIds.getResourceIds());
            query.remove(FamilyDBAdaptor.QueryParams.MOTHER.key());
        }
        if (StringUtils.isNotEmpty(query.getString(FamilyDBAdaptor.QueryParams.MEMBER.key()))) {
            MyResourceIds resourceIds = catalogManager.getIndividualManager()
                    .getIds(query.getString(FamilyDBAdaptor.QueryParams.MEMBER.key()), Long.toString(studyId), sessionId);
            query.put(FamilyDBAdaptor.QueryParams.MEMBER_ID.key(), resourceIds.getResourceIds());
            query.remove(FamilyDBAdaptor.QueryParams.MEMBER.key());
        }

        query.append(FamilyDBAdaptor.QueryParams.STUDY_ID.key(), studyId);

        QueryResult<Family> queryResult = familyDBAdaptor.get(query, options, userId);
        addMemberInformation(queryResult, studyId, sessionId);

        return queryResult;
    }

    public QueryResult<Family> count(String studyStr, Query query, String sessionId) throws CatalogException {
        String userId = catalogManager.getUserManager().getUserId(sessionId);
        long studyId = catalogManager.getStudyManager().getId(userId, studyStr);

        // The individuals introduced could be either ids or names. As so, we should use the smart resolutor to do this.
        // We change the FATHER, MOTHER and MEMBER parameters for FATHER_ID, MOTHER_ID and MEMBER_ID which is what the DBAdaptor
        // understands
        if (StringUtils.isNotEmpty(query.getString(FamilyDBAdaptor.QueryParams.FATHER.key()))) {
//            String studyStrAux = studyIds.size() == 1 ? Long.toString(studyIds.get(0)) : null;
            MyResourceIds resourceIds = catalogManager.getIndividualManager()
                    .getIds(query.getString(FamilyDBAdaptor.QueryParams.FATHER.key()), Long.toString(studyId), sessionId);
            query.put(FamilyDBAdaptor.QueryParams.FATHER_ID.key(), resourceIds.getResourceIds());
            query.remove(FamilyDBAdaptor.QueryParams.FATHER.key());
        }
        if (StringUtils.isNotEmpty(query.getString(FamilyDBAdaptor.QueryParams.MOTHER.key()))) {
//            String studyStrAux = studyIds.size() == 1 ? Long.toString(studyIds.get(0)) : null;
            MyResourceIds resourceIds = catalogManager.getIndividualManager()
                    .getIds(query.getString(FamilyDBAdaptor.QueryParams.MOTHER.key()), Long.toString(studyId), sessionId);
            query.put(FamilyDBAdaptor.QueryParams.MOTHER_ID.key(), resourceIds.getResourceIds());
            query.remove(FamilyDBAdaptor.QueryParams.MOTHER.key());
        }
        if (StringUtils.isNotEmpty(query.getString(FamilyDBAdaptor.QueryParams.MEMBER.key()))) {
//            String studyStrAux = studyIds.size() == 1 ? Long.toString(studyIds.get(0)) : null;
            MyResourceIds resourceIds = catalogManager.getIndividualManager()
                    .getIds(query.getString(FamilyDBAdaptor.QueryParams.MEMBER.key()), Long.toString(studyId), sessionId);
            query.put(FamilyDBAdaptor.QueryParams.MEMBER_ID.key(), resourceIds.getResourceIds());
            query.remove(FamilyDBAdaptor.QueryParams.MEMBER.key());
        }

        query.append(FamilyDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
        QueryResult<Long> queryResultAux = familyDBAdaptor.count(query, userId, StudyAclEntry.StudyPermissions.VIEW_FAMILIES);
        return new QueryResult<>("count", queryResultAux.getDbTime(), 0, queryResultAux.first(), queryResultAux.getWarningMsg(),
                queryResultAux.getErrorMsg(), Collections.emptyList());
    }

    @Override
    public List<QueryResult<Family>> delete(@Nullable String studyStr, String entries, ObjectMap params, String sessionId)
            throws CatalogException, IOException {
        return null;
    }

    @Override
    public QueryResult rank(String studyStr, Query query, String field, int numResults, boolean asc, String sessionId) throws
            CatalogException {
        return null;
    }

    @Override
    public QueryResult groupBy(@Nullable String studyStr, Query query, List<String> fields, QueryOptions options, String sessionId)
            throws CatalogException {
        return null;
    }

    @Override
    public QueryResult<Family> update(String studyStr, String entryStr, ObjectMap parameters, QueryOptions options, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(parameters, "Missing parameters");
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        MyResourceId resource = getId(entryStr, studyStr, sessionId);
        long familyId = resource.getResourceId();

        authorizationManager.checkFamilyPermission(resource.getStudyId(), familyId, resource.getUser(),
                FamilyAclEntry.FamilyPermissions.UPDATE);

        QueryResult<Family> familyQueryResult = familyDBAdaptor.get(familyId, new QueryOptions());
        if (familyQueryResult.getNumResults() == 0) {
            throw new CatalogException("Family " + familyId + " not found");
        }

        // In case the user is updating members or disease list, we will create the family variable. If it is != null, it will mean that
        // all or some of those parameters have been passed to be updated, and we will need to call the private validator to check if the
        // fields are valid.
        Family family = null;
        Iterator<Map.Entry<String, Object>> iterator = parameters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> param = iterator.next();
            FamilyDBAdaptor.QueryParams queryParam = FamilyDBAdaptor.QueryParams.getParam(param.getKey());
            switch (queryParam) {
                case NAME:
                    ParamUtils.checkAlias(parameters.getString(queryParam.key()), "name", configuration.getCatalog().getOffset());
                    break;
                case DISEASES:
                case MEMBERS:
                    if (family == null) {
                        // We parse the parameters to a family object
                        try {
                            ObjectMapper objectMapper = new ObjectMapper();
                            family = objectMapper.readValue(objectMapper.writeValueAsString(parameters), Family.class);
                        } catch (IOException e) {
                            logger.error("{}", e.getMessage(), e);
                            throw new CatalogException(e);
                        }
                    }
                    break;
                case DESCRIPTION:
                case ATTRIBUTES:
                    break;
                default:
                    throw new CatalogException("Cannot update " + queryParam);
            }
        }

        if (family != null) {
            // MEMBERS or DISEASES have been passed. We will complete the family object with the stored parameters that are not expected
            // to be updated
            if (family.getMembers() == null) {
                family.setMembers(familyQueryResult.first().getMembers());
            }
            if (family.getDiseases() == null) {
                family.setDiseases(familyQueryResult.first().getDiseases());
            }
            checkAndCreateAllIndividualsFromFamily(resource.getStudyId(), family, sessionId);

            ObjectMap tmpParams;
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                tmpParams = new ObjectMap(objectMapper.writeValueAsString(family));
            } catch (JsonProcessingException e) {
                logger.error("{}", e.getMessage(), e);
                throw new CatalogException(e);
            }

            if (parameters.containsKey(FamilyDBAdaptor.QueryParams.MEMBERS.key())) {
                parameters.put(FamilyDBAdaptor.QueryParams.MEMBERS.key(), tmpParams.get(FamilyDBAdaptor.QueryParams.MEMBERS.key()));
            }
            if (parameters.containsKey(FamilyDBAdaptor.QueryParams.DISEASES.key())) {
                parameters.put(FamilyDBAdaptor.QueryParams.DISEASES.key(), tmpParams.get(FamilyDBAdaptor.QueryParams.DISEASES.key()));
            }
        }

        QueryResult<Family> queryResult = familyDBAdaptor.update(familyId, parameters);
        auditManager.recordUpdate(AuditRecord.Resource.family, familyId, resource.getUser(), parameters, null, null);

        addMemberInformation(queryResult, resource.getStudyId(), sessionId);

        return queryResult;
    }

    @Override
    public QueryResult<AnnotationSet> createAnnotationSet(String id, @Nullable String studyStr, String variableSetId,
                                                          String annotationSetName, Map<String, Object> annotations,
                                                          Map<String, Object> attributes, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");
        ParamUtils.checkObj(annotations, "annotations");
        attributes = ParamUtils.defaultObject(attributes, HashMap<String, Object>::new);

        MyResourceId resourceId = getId(id, studyStr, sessionId);
        authorizationManager.checkFamilyPermission(resourceId.getStudyId(), resourceId.getResourceId(), resourceId.getUser(),
                FamilyAclEntry.FamilyPermissions.WRITE_ANNOTATIONS);
        MyResourceId variableSetResource = catalogManager.getStudyManager().getVariableSetId(variableSetId,
                Long.toString(resourceId.getStudyId()), sessionId);

        QueryResult<VariableSet> variableSet = studyDBAdaptor.getVariableSet(variableSetResource.getResourceId(), null,
                resourceId.getUser(), null);
        if (variableSet.getNumResults() == 0) {
            // Variable set must be confidential and the user does not have those permissions
            throw new CatalogAuthorizationException("Permission denied: User " + resourceId.getUser() + " cannot create annotations over "
                    + "that variable set");
        }

        QueryResult<AnnotationSet> annotationSet = createAnnotationSet(resourceId.getResourceId(), variableSet.first(),
                annotationSetName, annotations, catalogManager.getStudyManager().getCurrentRelease(resourceId.getStudyId()), attributes,
                familyDBAdaptor);

        auditManager.recordUpdate(AuditRecord.Resource.family, resourceId.getResourceId(), resourceId.getUser(),
                new ObjectMap("annotationSets", annotationSet.first()), "annotate", null);

        return annotationSet;
    }

    @Override
    public QueryResult<AnnotationSet> getAllAnnotationSets(String id, @Nullable String studyStr, String sessionId) throws CatalogException {
        MyResourceId resource = commonGetAllAnnotationSets(id, studyStr, sessionId);
        return familyDBAdaptor.getAnnotationSet(resource, null,
                StudyAclEntry.StudyPermissions.VIEW_FAMILY_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<ObjectMap> getAllAnnotationSetsAsMap(String id, @Nullable String studyStr, String sessionId) throws
            CatalogException {
        MyResourceId resource = commonGetAllAnnotationSets(id, studyStr, sessionId);
        return familyDBAdaptor.getAnnotationSetAsMap(resource, null,
                StudyAclEntry.StudyPermissions.VIEW_FAMILY_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<AnnotationSet> getAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        MyResourceId resource = commonGetAnnotationSet(id, studyStr, annotationSetName, sessionId);
        return familyDBAdaptor.getAnnotationSet(resource, annotationSetName,
                StudyAclEntry.StudyPermissions.VIEW_FAMILY_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<ObjectMap> getAnnotationSetAsMap(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        MyResourceId resource = commonGetAnnotationSet(id, studyStr, annotationSetName, sessionId);
        return familyDBAdaptor.getAnnotationSetAsMap(resource, annotationSetName,
                StudyAclEntry.StudyPermissions.VIEW_FAMILY_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<AnnotationSet> updateAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, Map<String,
            Object> newAnnotations, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");
        ParamUtils.checkObj(newAnnotations, "newAnnotations");

        MyResourceId resourceId = getId(id, studyStr, sessionId);
        authorizationManager.checkFamilyPermission(resourceId.getStudyId(), resourceId.getResourceId(), resourceId.getUser(),
                FamilyAclEntry.FamilyPermissions.WRITE_ANNOTATIONS);

        // Update the annotation
        QueryResult<AnnotationSet> queryResult = updateAnnotationSet(resourceId, annotationSetName, newAnnotations, familyDBAdaptor);

        if (queryResult == null || queryResult.getNumResults() == 0) {
            throw new CatalogException("There was an error with the update");
        }

        AnnotationSet annotationSet = queryResult.first();

        // Audit the changes
        AnnotationSet annotationSetUpdate = new AnnotationSet(annotationSet.getName(), annotationSet.getVariableSetId(),
                newAnnotations.entrySet().stream()
                        .map(entry -> new Annotation(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet()), annotationSet.getCreationDate(), 1, null);
        auditManager.recordUpdate(AuditRecord.Resource.family, resourceId.getResourceId(), resourceId.getUser(),
                new ObjectMap("annotationSets", Collections.singletonList(annotationSetUpdate)), "Update annotation", null);

        return queryResult;
    }

    @Override
    public QueryResult<AnnotationSet> deleteAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String
            sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");

        MyResourceId resourceId = getId(id, studyStr, sessionId);
        authorizationManager.checkFamilyPermission(resourceId.getStudyId(), resourceId.getResourceId(), resourceId.getUser(),
                FamilyAclEntry.FamilyPermissions.DELETE_ANNOTATIONS);

        QueryResult<AnnotationSet> annotationSet = familyDBAdaptor.getAnnotationSet(resourceId.getResourceId(), annotationSetName);
        if (annotationSet == null || annotationSet.getNumResults() == 0) {
            throw new CatalogException("Could not delete annotation set. The annotation set with name " + annotationSetName + " could not "
                    + "be found in the database.");
        }
        // We make this query because it will check the proper permissions in case the variable set is confidential
        studyDBAdaptor.getVariableSet(annotationSet.first().getVariableSetId(), new QueryOptions(), resourceId.getUser(), null);

        familyDBAdaptor.deleteAnnotationSet(resourceId.getResourceId(), annotationSetName);

        auditManager.recordDeletion(AuditRecord.Resource.family, resourceId.getResourceId(), resourceId.getUser(),
                new ObjectMap("annotationSets", Collections.singletonList(annotationSet.first())), "delete annotation", null);

        return annotationSet;
    }

    @Override
    public QueryResult<ObjectMap> searchAnnotationSetAsMap(String id, @Nullable String studyStr, String variableSetStr,
                                                           @Nullable String annotation, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");

        AbstractManager.MyResourceId resourceId = getId(id, studyStr, sessionId);
//        authorizationManager.checkFamilyPermission(resourceId.getStudyId(), resourceId.getResourceId(), resourceId.getUser(),
//                FamilyAclEntry.FamilyPermissions.VIEW_ANNOTATIONS);

        long variableSetId = -1;
        if (StringUtils.isNotEmpty(variableSetStr)) {
            variableSetId = catalogManager.getStudyManager().getVariableSetId(variableSetStr, Long.toString(resourceId.getStudyId()),
                    sessionId).getResourceId();
        }

        return familyDBAdaptor.searchAnnotationSetAsMap(resourceId, variableSetId, annotation,
                StudyAclEntry.StudyPermissions.VIEW_FAMILY_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<AnnotationSet> searchAnnotationSet(String id, @Nullable String studyStr, String variableSetStr,
                                                          @Nullable String annotation, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");

        AbstractManager.MyResourceId resourceId = getId(id, studyStr, sessionId);
//        authorizationManager.checkFamilyPermission(resourceId.getStudyId(), resourceId.getResourceId(), resourceId.getUser(),
//                FamilyAclEntry.FamilyPermissions.VIEW_ANNOTATIONS);

        long variableSetId = -1;
        if (StringUtils.isNotEmpty(variableSetStr)) {
            variableSetId = catalogManager.getStudyManager().getVariableSetId(variableSetStr, Long.toString(resourceId.getStudyId()),
                    sessionId).getResourceId();
        }

        return familyDBAdaptor.searchAnnotationSet(resourceId, variableSetId, annotation,
                StudyAclEntry.StudyPermissions.VIEW_FAMILY_ANNOTATIONS.toString());
    }

    private MyResourceId commonGetAllAnnotationSets(String id, @Nullable String studyStr, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        return getId(id, studyStr, sessionId);
//        authorizationManager.checkFamilyPermission(resourceId.getStudyId(), resourceId.getResourceId(), resourceId.getUser(),
//                FamilyAclEntry.FamilyPermissions.VIEW_ANNOTATIONS);
//        return resourceId.getResourceId();
    }

    private MyResourceId commonGetAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkAlias(annotationSetName, "annotationSetName", configuration.getCatalog().getOffset());
        return getId(id, studyStr, sessionId);
//        authorizationManager.checkFamilyPermission(resourceId.getStudyId(), resourceId.getResourceId(), resourceId.getUser(),
//                FamilyAclEntry.FamilyPermissions.VIEW_ANNOTATIONS);
//        return resourceId.getResourceId();
    }


    // **************************   ACLs  ******************************** //

    public List<QueryResult<FamilyAclEntry>> getAcls(String studyStr, String familyStr, String sessionId) throws CatalogException {
        MyResourceIds resource = getIds(familyStr, studyStr, sessionId);

        List<QueryResult<FamilyAclEntry>> familyAclList = new ArrayList<>(resource.getResourceIds().size());
        for (Long familyId : resource.getResourceIds()) {
            QueryResult<FamilyAclEntry> allFamilyAcls =
                    authorizationManager.getAllFamilyAcls(resource.getStudyId(), familyId, resource.getUser());
            allFamilyAcls.setId(String.valueOf(familyId));
            familyAclList.add(allFamilyAcls);
        }

        return familyAclList;
    }

    public List<QueryResult<FamilyAclEntry>> getAcl(String studyStr, String familyStr, String member, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(member, "member");

        MyResourceIds resource = getIds(familyStr, studyStr, sessionId);
        checkMembers(resource.getStudyId(), Arrays.asList(member));

        List<QueryResult<FamilyAclEntry>> familyAclList = new ArrayList<>(resource.getResourceIds().size());
        for (Long familyId : resource.getResourceIds()) {
            QueryResult<FamilyAclEntry> allFamilyAcls =
                    authorizationManager.getFamilyAcl(resource.getStudyId(), familyId, resource.getUser(), member);
            allFamilyAcls.setId(String.valueOf(familyId));
            familyAclList.add(allFamilyAcls);
        }

        return familyAclList;
    }

    public List<QueryResult<FamilyAclEntry>> updateAcl(String studyStr, String familyStr, String memberIds, AclParams familyAclParams,
                                                       String sessionId) throws CatalogException {
        if (StringUtils.isEmpty(familyStr)) {
            throw new CatalogException("Update ACL: Missing family parameter");
        }

        if (familyAclParams.getAction() == null) {
            throw new CatalogException("Invalid action found. Please choose a valid action to be performed.");
        }

        List<String> permissions = Collections.emptyList();
        if (StringUtils.isNotEmpty(familyAclParams.getPermissions())) {
            permissions = Arrays.asList(familyAclParams.getPermissions().trim().replaceAll("\\s", "").split(","));
            checkPermissions(permissions, FamilyAclEntry.FamilyPermissions::valueOf);
        }

        MyResourceIds resourceIds = getIds(familyStr, studyStr, sessionId);

        String collectionName = MongoDBAdaptorFactory.FAMILY_COLLECTION;
        // Check the user has the permissions needed to change permissions over those families
        for (Long familyId : resourceIds.getResourceIds()) {
            authorizationManager.checkFamilyPermission(resourceIds.getStudyId(), familyId, resourceIds.getUser(),
                    FamilyAclEntry.FamilyPermissions.SHARE);
        }

        // Validate that the members are actually valid members
        List<String> members;
        if (memberIds != null && !memberIds.isEmpty()) {
            members = Arrays.asList(memberIds.split(","));
        } else {
            members = Collections.emptyList();
        }
        checkMembers(resourceIds.getStudyId(), members);
//        catalogManager.getStudyManager().membersHavePermissionsInStudy(resourceIds.getStudyId(), members);

        switch (familyAclParams.getAction()) {
            case SET:
                return authorizationManager.setAcls(resourceIds.getStudyId(), resourceIds.getResourceIds(), members, permissions,
                        collectionName);
            case ADD:
                return authorizationManager.addAcls(resourceIds.getStudyId(), resourceIds.getResourceIds(), members, permissions,
                        collectionName);
            case REMOVE:
                return authorizationManager.removeAcls(resourceIds.getResourceIds(), members, permissions, collectionName);
            case RESET:
                return authorizationManager.removeAcls(resourceIds.getResourceIds(), members, null, collectionName);
            default:
                throw new CatalogException("Unexpected error occurred. No valid action found.");
        }
    }


    // **************************   Private methods  ******************************** //

//    private void checkAndCreateAllIndividualsFromFamily(long studyId, Family family, String sessionId) throws CatalogException {
//        if (family.getMother() == null) {
//            family.setMother(new Individual().setId(-1));
//        }
//        if (family.getFather() == null) {
//            family.setFather(new Individual().setId(-1));
//        }
//
//        // Check all individuals exist or can be created
//        checkAndCreateIndividual(studyId, family.getMother(), Individual.Sex.FEMALE, false, sessionId);
//        checkAndCreateIndividual(studyId, family.getFather(), Individual.Sex.MALE, false, sessionId);
//        if (family.getChildren() != null) {
//            for (Individual individual : family.getChildren()) {
//                checkAndCreateIndividual(studyId, individual, null, false, sessionId);
//            }
//        } else {
//            family.setChildren(Collections.emptyList());
//        }
//
//        // Create the ones that did not exist
//        checkAndCreateIndividual(studyId, family.getMother(), null, true, sessionId);
//        checkAndCreateIndividual(studyId, family.getFather(), null, true, sessionId);
//        for (Individual individual : family.getChildren()) {
//            checkAndCreateIndividual(studyId, individual, null, true, sessionId);
//        }
//    }

    /**
     * This method should be called two times. First time with !create to check if every individual is fine or can be created and a
     * second time with create to create the individual if is needed.
     *
     * @param studyId studyId.
     * @param individual individual.
     * @param sex When !create, it will check whether the individual sex corresponds with the sex given. If null, this will not be checked.
     * @param create Boolean indicating whether to make only checks or to create the individual.
     * @param sessionId sessionID.
     * @return Individual the individual object.
     * @throws CatalogException catalogException.
     */
    private Individual checkAndCreateIndividual(long studyId, Individual individual, Individual.Sex sex, boolean create, String sessionId)
            throws CatalogException {
        if (!create) {
            // Just check everything is fine

            if (individual.getId() > 0 || (StringUtils.isNotEmpty(individual.getName()) && StringUtils.isNumeric(individual.getName()))
                    && Long.parseLong(individual.getName()) > 0) {
                if (individual.getId() <= 0) {
                    individual.setId(Long.parseLong(individual.getName()));
                }
                QueryResult<Individual> indQueryResult = individualDBAdaptor.get(individual.getId(), QueryOptions.empty());
                if (indQueryResult.getNumResults() == 0) {
                    throw new CatalogException("Individual id '" + individual.getId() + "' does not exist");
                }
                individual = indQueryResult.first();

                // Check studyId of the individual
                long studyIdIndividual = individualDBAdaptor.getStudyId(individual.getId());
                if (studyId != studyIdIndividual) {
                    throw new CatalogException("Cannot create family in a different study than the one corresponding to the individuals.");
                }

                if (sex != null) {
                    if (individual.getSex() != sex) {
                        throw new CatalogException("The sex of the individual " + individual.getId() + " does not correspond with "
                                + "the expected sex: " + sex);
                    }
                }
            } else {
                if (StringUtils.isNotEmpty(individual.getName())) {
                    Query query = new Query()
                            .append(IndividualDBAdaptor.QueryParams.NAME.key(), individual.getName())
                            .append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);

                    QueryResult<Individual> individualQueryResult = individualDBAdaptor.get(query, QueryOptions.empty());
                    if (individualQueryResult.getNumResults() == 1) {
                        // Check the sex
                        if (sex != null && individualQueryResult.first().getSex() != sex) {
                            throw new CatalogException("The sex of the individual " + individual.getName() + " does not correspond with "
                                    + "the expected sex: " + sex);
                        }

                        individual.setId(individualQueryResult.first().getId());
                        individual.setFather(individualQueryResult.first().getFather());
                        individual.setMother(individualQueryResult.first().getMother());
                        individual.setMultiples(individualQueryResult.first().getMultiples());
                    } else {
                        // The individual has to be created.
                        if (sex != null && sex != individual.getSex()) {
                            throw new CatalogException("The sex of the individual " + individual.getName() + " does not correspond with "
                                    + "the expected sex: " + sex);
                        }
                    }
                }
            }
        } else {
            // Create if it was not already created
            if (individual.getId() <= 0 && StringUtils.isNotEmpty(individual.getName())) {
                individual.setSex(sex);
                // We create the individual
                QueryResult<Individual> individualQueryResult =
                        catalogManager.getIndividualManager().create(Long.toString(studyId), individual, new QueryOptions(), sessionId);
                if (individualQueryResult.getNumResults() == 0) {
                    throw new CatalogException("Unexpected error occurred when creating the individual");
                } else {
                    // We set the id
                    individual.setId(individualQueryResult.first().getId());
                }
            }
        }

        return individual;

    }

//    /**
//     * Validates that the family contains all the members needed to build a valid family.
//     *
//     * @param studyId
//     * @param family
//     * @throws CatalogException
//     */
//    private void validateFamily(long studyId, Family family) throws CatalogException {
//        if (family.getMembers() == null || family.getMembers().size() == 0) {
//            throw new CatalogException("Missing members in family");
//        }
//
//        // Store all the disease ids in a set
//        Set<String> diseaseSet = new HashSet<>();
//        if (family.getDiseases() != null) {
//            diseaseSet = family.getDiseases().stream().map(Disease::getId).collect(Collectors.toSet());
//        }
//
//        Set<String> familyMembers = new HashSet<>(family.getMembers().size());
//        for (Relatives relatives : family.getMembers()) {
//            // Check if the individual is correct or can be created
//            validateIndividual(studyId, relatives.getIndividual());
//            if (familyMembers.contains(relatives.getIndividual().getName())) {
//                throw new CatalogException("Multiple members with same name " + relatives.getIndividual().getName() + " found");
//            }
//            familyMembers.add(relatives.getIndividual().getName());
//        }
//
//        // We iterate again to check that all the references to father and mother are already in the familyMembers set. Otherwise, that
//        // individual information is missing.
//        for (Relatives relatives : family.getMembers()) {
//            // We are assuming that we are always going to have the relative name and not the id
//            if (relatives.getFather() != null && !familyMembers.contains(relatives.getFather().getName())) {
//                throw new CatalogException("Missing family member " + relatives.getFather().getName());
//            }
//            if (relatives.getMother() != null && !familyMembers.contains(relatives.getMother().getName())) {
//                throw new CatalogException("Missing family member " + relatives.getMother().getName());
//            }
//
//            // Check all the diseases are contained in the main array of diseases of the family
//            if (relatives.getDiseases() != null) {
//                if (!diseaseSet.containsAll(relatives.getDiseases())) {
//                    throw new CatalogException("Missing diseases that some family members have from the main disease list: "
//                            + StringUtils.join(relatives.getDiseases(), ","));
//                }
//            }
//        }
//
//    }

    private class MyFamily {
        private Individual individual;
        private Individual.Sex sex;
        private boolean hasParents;
        private List<String> children;

        MyFamily() {
            this.children = new ArrayList<>();
        }

        public Individual getIndividual() {
            return individual;
        }

        public void setIndividual(Individual individual) {
            this.individual = individual;
        }

        public Individual.Sex getSex() {
            return sex;
        }

        public void setSex(Individual.Sex sex) {
            this.sex = sex;
        }

        public boolean hasParents() {
            return hasParents;
        }

        public void setHasParents() {
            this.hasParents = true;
        }

        public List<String> getChildren() {
            return children;
        }

        public void addChild(String child) {
            this.children.add(child);
        }

    }

    /**
     * 1. Validates that the family contains all the members needed to build a valid family.
     * 2. Once the object is validated (no exception raised), it will automatically create individuals that are not yet in Catalog.
     * '  The family object is auto corrected so it can be directly inserted as is after having called this method.
     *
     * @param studyId study id.
     * @param family family object.
     * @param sessionId session id.
     * @throws CatalogException if the family object is not valid, or the individuals cannot be created due to a lack of permissions.
     */
    private void checkAndCreateAllIndividualsFromFamily(long studyId, Family family, String sessionId) throws CatalogException {

        // 1. Start validation of parameters.
        if (family.getMembers() == null || family.getMembers().size() == 0) {
            throw new CatalogException("Missing members in family");
        }

        // Store all the disease ids in a set
        Set<String> diseaseSet = new HashSet<>();
        if (family.getDiseases() != null) {
            diseaseSet = family.getDiseases().stream().map(OntologyTerm::getId).collect(Collectors.toSet());
        }

        Map<String, MyFamily> familyMembers = new HashMap<>(family.getMembers().size());

        // Just in case some or all the individuals already existed, we create a map of id -> name because the names are used to validate
        // the family is correct. However, when individuals are fetched, father and mother names are lost (not stored in the individual db)
        Map<Long, String> individualNameMap = new HashMap<>();
        for (Individual individual : family.getMembers()) {
            ParamUtils.checkAlias(individual.getName(), "member name", configuration.getCatalog().getOffset());
//            individual.setOntologyTerms(ParamUtils.defaultObject(individual.getOntologyTerms(), Collections::emptyList));
            // Check if the individual is correct or can be created
            checkAndCreateIndividual(studyId, individual, null, false, sessionId);
            if (individual.getId() > 0) {
                individualNameMap.put(individual.getId(), individual.getName());
            }
        }
        if (individualNameMap.size() > 0) {
            // We probably need to assign names were needed
            for (Individual individual : family.getMembers()) {
                if (individual.getMother() != null && individual.getMother().getId() > 0) {
                    String motherName = individualNameMap.get(individual.getMother().getId());
                    if (StringUtils.isEmpty(motherName)) {
                        throw new CatalogException("Incomplete family. Mother name not found under id " + individual.getMother().getId());
                    }
                }
                if (individual.getFather() != null && individual.getFather().getId() > 0) {
                    String fatherName = individualNameMap.get(individual.getFather().getId());
                    if (StringUtils.isEmpty(fatherName)) {
                        throw new CatalogException("Incomplete family. Father name not found under id " + individual.getFather().getId());
                    }
                }
            }
        }

        Map<String, Set<String>> memberSiblings = new HashMap<>();
        for (Individual individual : family.getMembers()) {
            String individualName = individual.getName();
            if (familyMembers.containsKey(individualName) && familyMembers.get(individualName).getIndividual() != null) {
                throw new CatalogException("Multiple members with same name " + individual.getName() + " found");
            }
            if (!familyMembers.containsKey(individualName)) {
                familyMembers.put(individualName, new MyFamily());
            }
            familyMembers.get(individualName).setIndividual(individual);

            if (individual.getFather() != null && StringUtils.isNotEmpty(individual.getFather().getName())) {
                String fatherName = individual.getFather().getName();
                if (!familyMembers.containsKey(fatherName)) {
                    familyMembers.put(fatherName, new MyFamily());
                }
                familyMembers.get(fatherName).addChild(individualName);
                familyMembers.get(fatherName).setSex(Individual.Sex.MALE);
                familyMembers.get(individualName).setHasParents();
            }
            if (individual.getMother() != null && StringUtils.isNotEmpty(individual.getMother().getName())) {
                String motherName = individual.getMother().getName();
                if (!familyMembers.containsKey(motherName)) {
                    familyMembers.put(motherName, new MyFamily());
                }
                familyMembers.get(motherName).addChild(individualName);
                familyMembers.get(motherName).setSex(Individual.Sex.FEMALE);
                familyMembers.get(individualName).setHasParents();
            }

            // Check all the diseases are contained in the main array of diseases of the family
            if (!diseaseSet.containsAll(individual.getOntologyTerms().stream().map(OntologyTerm::getId).collect(Collectors.toSet()))) {
                throw new CatalogException("Missing disease annotations that some family members have: "
                        + individual.getOntologyTerms().stream().map(OntologyTerm::getId).collect(Collectors.joining(",")));
            }

            // Add any siblings to the siblings map if any
            if (individual.getMultiples() != null) {
                if (individual.getMultiples().getSiblings() != null && !individual.getMultiples().getSiblings().isEmpty()) {
                    memberSiblings.put(individualName, new HashSet<>(individual.getMultiples().getSiblings()));
                }
            }
        }

        // Check all the siblings are properly crossed-referenced
        for (Map.Entry<String, Set<String>> entry : memberSiblings.entrySet()) {
            // Add all the siblings to the set
            Set<String> allSiblings = new HashSet<>(entry.getValue());
            allSiblings.add(entry.getKey());

            for (String member : entry.getValue()) {
                // Remove current member from the siblings set
                allSiblings.remove(member);

                // Check the member (sibling) has exactly the same siblings defined in its set
                if (memberSiblings.get(member) == null) {
                    throw new CatalogException("Missing sibling " + member + " information");
                }
                if (memberSiblings.get(member).size() != allSiblings.size()) {
                    throw new CatalogException("The number of siblings contained by " + member + " does not match the ones "
                            + "contained by " + entry.getKey());
                }
                if (!memberSiblings.get(member).containsAll(allSiblings)) {
                    throw new CatalogException("Some of the siblings contained by " + member + " does not match the ones "
                            + "contained by " + entry.getKey());
                }

                // Restore/add the current member to the siblings set
                allSiblings.add(member);
            }
        }


        // We will assume that all the founders are in the first level.
        // Look for the founders. hasParents = false
        List<Set<String>> familyLevels; // Level 0 -> founders, level 1 -> children of founders, 2 -> children of children of founders ...
        familyLevels = new ArrayList<>();
        familyLevels.add(new HashSet<>());
        for (Map.Entry<String, MyFamily> entry : familyMembers.entrySet()) {
            // Check that all entries have a proper individual
            if (entry.getValue().getIndividual() == null) {
                throw new CatalogException("Missing family member " + entry.getKey());
            }

            if (!entry.getValue().hasParents()) {
                familyLevels.get(0).add(entry.getKey());
            }
        }
        populateFamily(familyLevels, familyMembers, 0);

        // Check all the family members are contained in the familyLevels (no orphan childs)
        int count = 0;
        for (Set<String> familyLevel : familyLevels) {
            count += familyLevel.size();
        }
        if (familyMembers.size() < count) {
            throw new CatalogException("Unrelated children found. Please, relate all the members of the family.");
        }

        // 2. Create individuals if they do not exist. They will be created by levels
        for (Set<String> familyLevel : familyLevels) {
            for (String member : familyLevel) {
                Individual individual = familyMembers.get(member).getIndividual();
                // Because we have been creating users in the proper order, the ids of the parents should already exist when the children
                // are to be created. If that's the case, we assign that new individual information
                if (individual.getFather() != null && StringUtils.isNotEmpty(individual.getFather().getName())) {
                    individual.setFather(familyMembers.get(individual.getFather().getName()).getIndividual());
                }
                if (individual.getMother() != null && StringUtils.isNotEmpty(individual.getMother().getName())) {
                    individual.setMother(familyMembers.get(individual.getMother().getName()).getIndividual());
                }

                checkAndCreateIndividual(studyId, individual, familyMembers.get(member).getSex(), true, sessionId);
            }
        }

        // Now that all the individuals have an id, we will update the parents id's just in case they were just created in catalog
        for (Individual individual : family.getMembers()) {
            if (individual.getFather() != null && StringUtils.isNotEmpty(individual.getFather().getName())) {
                individual.getFather().setId(familyMembers.get(individual.getFather().getName()).getIndividual().getId());
            }
            if (individual.getMother() != null && StringUtils.isNotEmpty(individual.getMother().getName())) {
                individual.getMother().setId(familyMembers.get(individual.getMother().getName()).getIndividual().getId());
            }
        }
    }

//    /**
//     * Auxiliar method to get either the id of an individual or the name to be used as a unique identifier of the individual.
//     *
//     * @param individual individual.
//     * @return the id or name.
//     */
//    private String getIndividualIdOrName(Individual individual) {
//        return individual.getId() > 0 ? String.valueOf(individual.getId()) : individual.getName();
//    }

    private void populateFamily(List<Set<String>> familyLevels, Map<String, MyFamily> familyMembers, int level) {
        for (String individualName : familyLevels.get(level)) {
            if (familyMembers.get(individualName).getChildren().size() > 0) {
                if (familyLevels.size() < level + 2) {
                    familyLevels.add(new HashSet<>());
                }
                familyLevels.get(level + 1).addAll(familyMembers.get(individualName).getChildren());
            }
        }
        // If we have added new children (there is other level)...
        if (familyLevels.size() == level + 2) {
            populateFamily(familyLevels, familyMembers, level + 1);
        }
    }

    private void addMemberInformation(QueryResult<Family> queryResult, long studyId, String sessionId) {
        if (queryResult.getNumResults() == 0) {
            return;
        }
        for (Family family : queryResult.getResult()) {
            if (family.getMembers() != null && !family.getMembers().isEmpty()) {
                List<Long> individualIds = family.getMembers().stream()
                        .map(Individual::getId)
                        .collect(Collectors.toList());
                Query query = new Query()
                        .append(IndividualDBAdaptor.QueryParams.ID.key(), individualIds);
                try {
                    QueryResult<Individual> individualQueryResult = catalogManager.getIndividualManager()
                            .get(String.valueOf(studyId), query, QueryOptions.empty(), sessionId);
                    if (individualQueryResult.getNumResults() == family.getMembers().size()) {
                        family.setMembers(individualQueryResult.getResult());
                    } else {
                        throw new CatalogException("Could not fetch all the individuals from family");
                    }
                } catch (CatalogException e) {
                    logger.warn("Could not retrieve individual information to complete family object, {}", e.getMessage(), e);
                    queryResult.setWarningMsg("Could not retrieve individual information to complete family object" + e.getMessage());
                }
            }
        }
    }

}
