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

import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.commons.utils.CollectionUtils;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.audit.AuditRecord;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.DBIterator;
import org.opencb.opencga.catalog.db.api.IndividualDBAdaptor;
import org.opencb.opencga.catalog.db.api.SampleDBAdaptor;
import org.opencb.opencga.catalog.db.mongodb.MongoDBAdaptorFactory;
import org.opencb.opencga.catalog.exceptions.CatalogAuthorizationException;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.models.*;
import org.opencb.opencga.core.models.acls.AclParams;
import org.opencb.opencga.core.models.acls.permissions.IndividualAclEntry;
import org.opencb.opencga.core.models.acls.permissions.StudyAclEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.opencb.opencga.catalog.auth.authorization.CatalogAuthorizationManager.checkPermissions;

/**
 * Created by hpccoll1 on 19/06/15.
 */
public class IndividualManager extends AnnotationSetManager<Individual> {

    protected static Logger logger = LoggerFactory.getLogger(IndividualManager.class);
    private UserManager userManager;
    private StudyManager studyManager;

    private static final Map<Individual.KaryotypicSex, Individual.Sex> KARYOTYPIC_SEX_SEX_MAP;
    static {
        KARYOTYPIC_SEX_SEX_MAP = new HashMap<>();
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.UNKNOWN, Individual.Sex.UNKNOWN);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XX, Individual.Sex.FEMALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XO, Individual.Sex.FEMALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XXX, Individual.Sex.FEMALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XXXX, Individual.Sex.FEMALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XY, Individual.Sex.MALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XXY, Individual.Sex.MALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XXYY, Individual.Sex.MALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XXXY, Individual.Sex.MALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.XYY, Individual.Sex.MALE);
        KARYOTYPIC_SEX_SEX_MAP.put(Individual.KaryotypicSex.OTHER, Individual.Sex.UNDETERMINED);
    }

    IndividualManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                             DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory,
                             Configuration configuration) {
        super(authorizationManager, auditManager, catalogManager, catalogDBAdaptorFactory, ioManagerFactory, configuration);

        this.userManager = catalogManager.getUserManager();
        this.studyManager = catalogManager.getStudyManager();
    }

    public QueryResult<Individual> create(long studyId, String name, String family, long fatherId, long motherId, Individual.Sex sex,
                                          String ethnicity, String populationName, String populationSubpopulation,
                                          String populationDescription, String dateOfBirth, Individual.KaryotypicSex karyotypicSex,
                                          Individual.LifeStatus lifeStatus, Individual.AffectationStatus affectationStatus,
                                          QueryOptions options, String sessionId) throws CatalogException {
        options = ParamUtils.defaultObject(options, QueryOptions::new);
        sex = ParamUtils.defaultObject(sex, Individual.Sex.UNKNOWN);
        logger.info(Long.toString(configuration.getCatalog().getOffset()));
        ParamUtils.checkAlias(name, "name", configuration.getCatalog().getOffset());
        family = ParamUtils.defaultObject(family, "");
        ethnicity = ParamUtils.defaultObject(ethnicity, "");
        populationName = ParamUtils.defaultObject(populationName, "");
        populationSubpopulation = ParamUtils.defaultObject(populationSubpopulation, "");
        populationDescription = ParamUtils.defaultObject(populationDescription, "");
        karyotypicSex = ParamUtils.defaultObject(karyotypicSex, Individual.KaryotypicSex.UNKNOWN);
        lifeStatus = ParamUtils.defaultObject(lifeStatus, Individual.LifeStatus.UNKNOWN);
        affectationStatus = ParamUtils.defaultObject(affectationStatus, Individual.AffectationStatus.UNKNOWN);
        if (StringUtils.isEmpty(dateOfBirth)) {
            dateOfBirth = "";
        } else {
            if (!TimeUtils.isValidFormat("yyyyMMdd", dateOfBirth)) {
                throw new CatalogException("Invalid date of birth format. Valid format yyyyMMdd");
            }
        }

        String userId = userManager.getUserId(sessionId);
        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.WRITE_INDIVIDUALS);

        Individual individual = new Individual(0, name, fatherId, motherId, family, sex, karyotypicSex, ethnicity,
                new Individual.Population(populationName, populationSubpopulation, populationDescription), lifeStatus, affectationStatus,
                dateOfBirth, false, studyManager.getCurrentRelease(studyId), Collections.emptyList(),
                new ArrayList<>());

        QueryResult<Individual> queryResult = individualDBAdaptor.insert(individual, studyId, options);
        auditManager.recordCreation(AuditRecord.Resource.individual, queryResult.first().getId(), userId, queryResult.first(), null, null);

        // Add sample information
        addSampleInformation(queryResult, studyId, userId);
        return queryResult;
    }

    @Override
    public QueryResult<Individual> get(String studyStr, Query query, QueryOptions options, String sessionId) throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getUserId(sessionId);
        long studyId = studyManager.getId(userId, studyStr);

        query.append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);

        QueryResult<Individual> individualQueryResult = individualDBAdaptor.get(query, options, userId);

        // Add sample information
        addSampleInformation(individualQueryResult, studyId, userId);
        return individualQueryResult;
    }

    public QueryResult<Individual> get(long studyId, Query query, QueryOptions options, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(sessionId, "sessionId");
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getUserId(sessionId);
        query.append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
        QueryResult<Individual> queryResult = individualDBAdaptor.get(query, options, userId);
//        authorizationManager.filterIndividuals(userId, studyId, queryResult.getResult());

        // Add sample information
        addSampleInformation(queryResult, studyId, userId);
        return queryResult;
    }

    @Override
    public DBIterator<Individual> iterator(String studyStr, Query query, QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkObj(sessionId, "sessionId");
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        String userId = userManager.getUserId(sessionId);
        long studyId = studyManager.getId(userId, studyStr);
        query.append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);

        return individualDBAdaptor.iterator(query, options, userId);
    }


    public List<QueryResult<Individual>> restore(String individualIdStr, QueryOptions options, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(individualIdStr, "id");
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        MyResourceIds resource = getIds(individualIdStr, null, sessionId);

        List<QueryResult<Individual>> queryResultList = new ArrayList<>(resource.getResourceIds().size());
        for (Long individualId : resource.getResourceIds()) {
            QueryResult<Individual> queryResult = null;
            try {
                authorizationManager.checkIndividualPermission(resource.getStudyId(), individualId, resource.getUser(),
                        IndividualAclEntry.IndividualPermissions.DELETE);
                queryResult = individualDBAdaptor.restore(individualId, options);

                auditManager.recordRestore(AuditRecord.Resource.individual, individualId, resource.getUser(), Status.DELETED,
                        Status.READY, "Individual restore", null);
            } catch (CatalogAuthorizationException e) {
                auditManager.recordRestore(AuditRecord.Resource.individual, individualId, resource.getUser(), null, null, e.getMessage(),
                        null);
                queryResult = new QueryResult<>("Restore individual " + individualId);
                queryResult.setErrorMsg(e.getMessage());
            } catch (CatalogException e) {
                e.printStackTrace();
                queryResult = new QueryResult<>("Restore individual " + individualId);
                queryResult.setErrorMsg(e.getMessage());
            } finally {
                queryResultList.add(queryResult);
            }
        }

        return queryResultList;
    }

    public List<QueryResult<Individual>> restore(Query query, QueryOptions options, String sessionId) throws CatalogException {
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, IndividualDBAdaptor.QueryParams.ID.key());
        QueryResult<Individual> individualQueryResult = individualDBAdaptor.get(query, queryOptions);
        List<Long> individualIds = individualQueryResult.getResult().stream().map(Individual::getId).collect(Collectors.toList());
        String individualStr = StringUtils.join(individualIds, ",");
        return restore(individualStr, options, sessionId);
    }

    @Override
    public Long getStudyId(long individualId) throws CatalogException {
        return individualDBAdaptor.getStudyId(individualId);
    }

    @Override
    public MyResourceId getId(String individualStr, @Nullable String studyStr, String sessionId) throws CatalogException {
        if (StringUtils.isEmpty(individualStr)) {
            throw new CatalogException("Missing individual parameter");
        }

        String userId;
        long studyId;
        long individualId;

        if (StringUtils.isNumeric(individualStr) && Long.parseLong(individualStr) > configuration.getCatalog().getOffset()) {
            individualId = Long.parseLong(individualStr);
            individualDBAdaptor.exists(individualId);
            studyId = individualDBAdaptor.getStudyId(individualId);
            userId = userManager.getUserId(sessionId);
        } else {
            if (individualStr.contains(",")) {
                throw new CatalogException("More than one individual found");
            }

            userId = userManager.getUserId(sessionId);
            studyId = studyManager.getId(userId, studyStr);

            Query query = new Query()
                    .append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(IndividualDBAdaptor.QueryParams.NAME.key(), individualStr);
            QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, IndividualDBAdaptor.QueryParams.ID.key());
            QueryResult<Individual> individualQueryResult = individualDBAdaptor.get(query, queryOptions);
            if (individualQueryResult.getNumResults() == 1) {
                individualId = individualQueryResult.first().getId();
            } else {
                if (individualQueryResult.getNumResults() == 0) {
                    throw new CatalogException("Individual " + individualStr + " not found in study " + studyStr);
                } else {
                    throw new CatalogException("More than one individual found under " + individualStr + " in study " + studyStr);
                }
            }
        }

        return new MyResourceId(userId, studyId, individualId);
    }

    @Override
    public MyResourceIds getIds(String individualStr, @Nullable String studyStr, String sessionId) throws CatalogException {
        if (StringUtils.isEmpty(individualStr)) {
            throw new CatalogException("Missing individual parameter");
        }

        String userId;
        long studyId;
        List<Long> individualIds;

        if (StringUtils.isNumeric(individualStr) && Long.parseLong(individualStr) > configuration.getCatalog().getOffset()) {
            individualIds = Arrays.asList(Long.parseLong(individualStr));
            individualDBAdaptor.exists(individualIds.get(0));
            studyId = individualDBAdaptor.getStudyId(individualIds.get(0));
            userId = userManager.getUserId(sessionId);
        } else {
            userId = userManager.getUserId(sessionId);
            studyId = studyManager.getId(userId, studyStr);

            List<String> individualSplit = Arrays.asList(individualStr.split(","));
            Query query = new Query()
                    .append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(IndividualDBAdaptor.QueryParams.NAME.key(), individualSplit);
            QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, IndividualDBAdaptor.QueryParams.ID.key());
            QueryResult<Individual> individualQueryResult = individualDBAdaptor.get(query, queryOptions);
            if (individualQueryResult.getNumResults() == individualSplit.size()) {
                individualIds = individualQueryResult.getResult().stream().map(Individual::getId).collect(Collectors.toList());
            } else {
                throw new CatalogException("Found only " + individualQueryResult.getNumResults() + " out of the " + individualSplit.size()
                        + " individuals looked for in study " + studyStr);
            }
        }

        return new MyResourceIds(userId, studyId, individualIds);
    }

    @Override
    public QueryResult<Individual> search(String studyStr, Query query, QueryOptions options, String sessionId) throws CatalogException {
        String userId = userManager.getUserId(sessionId);
        long studyId = studyManager.getId(userId, studyStr);

        Query finalQuery = new Query(query);
        fixQuery(studyId, finalQuery, sessionId);

        finalQuery.append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
        QueryResult<Individual> queryResult = individualDBAdaptor.get(finalQuery, options, userId);
//        authorizationManager.filterIndividuals(userId, studyId, queryResultAux.getResult());

        // Add sample information
        addSampleInformation(queryResult, studyId, userId);
        return queryResult;
    }

    @Override
    public QueryResult<Individual> count(String studyStr, Query query, String sessionId) throws CatalogException {
        String userId = userManager.getUserId(sessionId);
        long studyId = studyManager.getId(userId, studyStr);

        Query finalQuery = new Query(query);
        fixQuery(studyId, finalQuery, sessionId);

        finalQuery.append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
        QueryResult<Long> queryResultAux = individualDBAdaptor.count(finalQuery, userId, StudyAclEntry.StudyPermissions.VIEW_INDIVIDUALS);
        return new QueryResult<>("count", queryResultAux.getDbTime(), 0, queryResultAux.first(), queryResultAux.getWarningMsg(),
                queryResultAux.getErrorMsg(), Collections.emptyList());
    }

    @Override
    public QueryResult<Individual> create(String studyStr, Individual individual, QueryOptions options, String sessionId)
            throws CatalogException {
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        List<Sample> samples = individual.getSamples();

        ParamUtils.checkAlias(individual.getName(), "name", configuration.getCatalog().getOffset());
        individual.setFamily(ParamUtils.defaultObject(individual.getFamily(), ""));
        individual.setEthnicity(ParamUtils.defaultObject(individual.getEthnicity(), ""));
        individual.setSpecies(ParamUtils.defaultObject(individual.getSpecies(), Individual.Species::new));
        individual.setPopulation(ParamUtils.defaultObject(individual.getPopulation(), Individual.Population::new));
        individual.setLifeStatus(ParamUtils.defaultObject(individual.getLifeStatus(), Individual.LifeStatus.UNKNOWN));
        individual.setKaryotypicSex(ParamUtils.defaultObject(individual.getKaryotypicSex(), Individual.KaryotypicSex.UNKNOWN));
        individual.setSex(ParamUtils.defaultObject(individual.getSex(), Individual.Sex.UNKNOWN));
        individual.setAffectationStatus(ParamUtils.defaultObject(individual.getAffectationStatus(), Individual.AffectationStatus.UNKNOWN));
        individual.setOntologyTerms(ParamUtils.defaultObject(individual.getOntologyTerms(), Collections.emptyList()));
        individual.setAnnotationSets(ParamUtils.defaultObject(individual.getAnnotationSets(), Collections.emptyList()));
        individual.setAnnotationSets(validateAnnotationSets(individual.getAnnotationSets()));
        individual.setAttributes(ParamUtils.defaultObject(individual.getAttributes(), Collections.emptyMap()));
        individual.setSamples(Collections.emptyList());
        individual.setStatus(new Status());
        individual.setCreationDate(TimeUtils.getTime());

        String userId = userManager.getUserId(sessionId);
        long studyId = studyManager.getId(userId, studyStr);
        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.WRITE_INDIVIDUALS);

        individual.setRelease(studyManager.getCurrentRelease(studyId));

        QueryResult<Individual> queryResult = individualDBAdaptor.insert(individual, studyId, options);
        auditManager.recordCreation(AuditRecord.Resource.individual, queryResult.first().getId(), userId, queryResult.first(), null, null);

        // Check samples
        if (samples != null && samples.size() > 0) {
            List<String> errorMessages = new ArrayList<>();
            for (Sample sample : samples) {
                try {
                    MyResourceId resource = catalogManager.getSampleManager().getId(sample.getName(), String.valueOf(studyId), sessionId);
                    ObjectMap params = new ObjectMap(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key(), queryResult.first().getId());
                    try {
                        // We update the sample metadata to relate it to the individual
                        sampleDBAdaptor.update(resource.getResourceId(), params);
                    } catch (CatalogDBException e1) {
                        logger.error("Internal error when attempting to associate the individual to the sample. {}" + sample.getName(),
                                e1.getMessage(), e1);
                        errorMessages.add("Internal error when attempting to associate the individual to the sample " + sample.getName()
                                + ". " + e1.getMessage());
                    }
                } catch (CatalogException e) {
                    // It seems the sample does not exist so we will attempt to create it
                    try {
                        Individual ind = new Individual().setName(String.valueOf(queryResult.first().getId()));
                        catalogManager.getSampleManager().create(String.valueOf(studyId), sample, ind, QueryOptions.empty(), sessionId);
                    } catch (CatalogException e1) {
                        logger.error("The sample " + sample.getName() + " could not be created. Please, check the parameters. {}",
                                e1.getMessage(), e1);
                        errorMessages.add("The sample " + sample.getName() + " could not be created. Please, check the parameters. "
                            + e1.getMessage());
                    }
                }
            }

            if (errorMessages.size() > 0) {
                queryResult.setErrorMsg(StringUtils.join(errorMessages, "\n"));
            }

            // Add sample information
            addSampleInformation(queryResult, studyId, userId);
        }

        return queryResult;
    }

    @Override
    public QueryResult<Individual> update(String studyStr, String entryStr, ObjectMap parameters, QueryOptions options, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(parameters, "parameters");
        options = ParamUtils.defaultObject(options, QueryOptions::new);

        MyResourceId resource = getId(entryStr, studyStr, sessionId);
        String userId = resource.getUser();
        long studyId = resource.getStudyId();
        long individualId = resource.getResourceId();

        authorizationManager.checkIndividualPermission(studyId, individualId, userId, IndividualAclEntry.IndividualPermissions.UPDATE);
        List<String> samples = null;

        for (Map.Entry<String, Object> param : parameters.entrySet()) {
            IndividualDBAdaptor.QueryParams queryParam = IndividualDBAdaptor.QueryParams.getParam(param.getKey());
            switch (queryParam) {
                case NAME:
                    ParamUtils.checkAlias(parameters.getString(queryParam.key()), "name", configuration.getCatalog().getOffset());

                    String myName = parameters.getString(IndividualDBAdaptor.QueryParams.NAME.key());
                    Query query = new Query()
                            .append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                            .append(IndividualDBAdaptor.QueryParams.NAME.key(), myName);
                    if (individualDBAdaptor.count(query).first() > 0) {
                        throw new CatalogException("Individual name " + myName + " already in use");
                    }
                    break;
                case DATE_OF_BIRTH:
                    if (StringUtils.isEmpty((String) param.getValue())) {
                        parameters.put(param.getKey(), "");
                    } else {
                        if (!TimeUtils.isValidFormat("yyyyMMdd", (String) param.getValue())) {
                            throw new CatalogException("Invalid date of birth format. Valid format yyyyMMdd");
                        }
                    }
                    break;
                case KARYOTYPIC_SEX:
                    try {
                        Individual.KaryotypicSex.valueOf(String.valueOf(param.getValue()));
                    } catch (IllegalArgumentException e) {
                        logger.error("Invalid karyotypic sex found: {}", e.getMessage(), e);
                        throw new CatalogException("Invalid karyotypic sex detected");
                    }
                    break;
                case SEX:
                    try {
                        Individual.Sex.valueOf(String.valueOf(param.getValue()));
                    } catch (IllegalArgumentException e) {
                        logger.error("Invalid sex found: {}", e.getMessage(), e);
                        throw new CatalogException("Invalid sex detected");
                    }
                    break;
                case MULTIPLES:
                    // Check individual names exist
                    Map<String, Object> multiples = (Map<String, Object>) param.getValue();
                    List<String> siblingList = (List<String>) multiples.get("siblings");
                    query = new Query()
                            .append(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                            .append(IndividualDBAdaptor.QueryParams.NAME.key(), StringUtils.join(siblingList, ","));
                    QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, IndividualDBAdaptor.QueryParams.ID.key());
                    QueryResult<Individual> individualQueryResult = individualDBAdaptor.get(query, queryOptions);
                    if (individualQueryResult.getNumResults() < siblingList.size()) {
                        int missing = siblingList.size() - individualQueryResult.getNumResults();
                        throw new CatalogDBException("Missing " + missing + " siblings in the database.");
                    }
                    break;
                case SAMPLES:
                    samples = parameters.getAsStringList(param.getKey());
                    break;
                case FATHER:
                case MOTHER:
                case ETHNICITY:
                case POPULATION_DESCRIPTION:
                case POPULATION_NAME:
                case POPULATION_SUBPOPULATION:
                case ONTOLOGY_TERMS:
                case LIFE_STATUS:
                case AFFECTATION_STATUS:
                    break;
                default:
                    throw new CatalogException("Cannot update " + queryParam);
            }
        }

        if (parameters.containsKey(IndividualDBAdaptor.QueryParams.SAMPLES.key())) {
            parameters.remove(IndividualDBAdaptor.QueryParams.SAMPLES.key());
        }
        MyResourceIds sampleResource = null;
        if (samples != null && !samples.isEmpty()) {
            // 1. Get the sample ids to relate to the individual. We get the ids now to fail before doing anything in case we need to
            sampleResource = catalogManager.getSampleManager().getIds(StringUtils.join(samples, ","), String.valueOf(studyId), sessionId);
        }

        if (StringUtils.isNotEmpty(parameters.getString(IndividualDBAdaptor.QueryParams.FATHER.key()))) {
            MyResourceId tmpResource =
                    getId(parameters.getString(IndividualDBAdaptor.QueryParams.FATHER.key()), String.valueOf(studyId), sessionId);
            parameters.remove(IndividualDBAdaptor.QueryParams.FATHER.key());
            parameters.put(IndividualDBAdaptor.QueryParams.FATHER_ID.key(), tmpResource.getResourceId());
        }
        if (StringUtils.isNotEmpty(parameters.getString(IndividualDBAdaptor.QueryParams.MOTHER.key()))) {
            MyResourceId tmpResource =
                    getId(parameters.getString(IndividualDBAdaptor.QueryParams.MOTHER.key()), String.valueOf(studyId), sessionId);
            parameters.remove(IndividualDBAdaptor.QueryParams.MOTHER.key());
            parameters.put(IndividualDBAdaptor.QueryParams.MOTHER_ID.key(), tmpResource.getResourceId());
        }

//        options.putAll(parameters); //FIXME: Use separated params and options, or merge
        QueryResult<Individual> queryResult;
        if (!parameters.isEmpty()) {
            queryResult = individualDBAdaptor.update(individualId, parameters);
            auditManager.recordUpdate(AuditRecord.Resource.individual, individualId, userId, parameters, null, null);
        } else {
            queryResult = individualDBAdaptor.get(individualId, options, userId);
        }

        if (sampleResource != null) {
            // Update samples

            // 1. Remove all references to the individual in all the samples
            Query query = new Query()
                    .append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key(), individualId);
            ObjectMap params = new ObjectMap(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key(), -1L);
            sampleDBAdaptor.update(query, params);

            // 2. Add reference to the current individual in the samples indicated
            query = new Query()
                    .append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(SampleDBAdaptor.QueryParams.ID.key(), sampleResource.getResourceIds());
            params = new ObjectMap(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key(), individualId);
            sampleDBAdaptor.update(query, params);
        }

        // Add sample information
        addSampleInformation(queryResult, studyId, userId);

        return queryResult;
    }

    public List<QueryResult<Individual>> delete(@Nullable String studyStr, String individualIdStr, ObjectMap options, String sessionId)
            throws CatalogException, IOException {
        ParamUtils.checkParameter(individualIdStr, "id");
        options = ParamUtils.defaultObject(options, ObjectMap::new);

        MyResourceIds resourceId = getIds(individualIdStr, studyStr, sessionId);
        List<Long> individualIds = resourceId.getResourceIds();
        String userId = resourceId.getUser();

        List<QueryResult<Individual>> queryResultList = new ArrayList<>(individualIds.size());
        for (Long individualId : individualIds) {
            QueryResult<Individual> queryResult = null;
            try {
                authorizationManager.checkIndividualPermission(resourceId.getStudyId(), individualId, userId,
                        IndividualAclEntry.IndividualPermissions.DELETE);

                // We can delete an individual if their samples can be deleted
                // We obtain the samples associated to the individual and check if those can be deleted
                Query query = new Query()
                        .append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), resourceId.getStudyId())
                        .append(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key(), individualId);
                QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.ID.key());
                QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query, queryOptions);

                if (sampleQueryResult.getNumResults() > 0) {
                    List<Long> sampleIds = sampleQueryResult.getResult().stream().map(Sample::getId).collect(Collectors.toList());
                    MyResourceIds sampleResource = new MyResourceIds(resourceId.getUser(), resourceId.getStudyId(), sampleIds);
                    // FIXME:
                    // We are first checking and deleting later because that delete method does not check if all the samples can be deleted
                    // directly. Instead, it makes a loop and checks one by one. Changes should be done there.
                    catalogManager.getSampleManager().checkCanDeleteSamples(sampleResource);
                    catalogManager.getSampleManager().delete(Long.toString(resourceId.getStudyId()), StringUtils.join(sampleIds, ","),
                            QueryOptions.empty(), sessionId);
                }

                // Get the individual info before the update
                QueryResult<Individual> individualQueryResult = individualDBAdaptor.get(individualId, QueryOptions.empty());

                String newIndividualName = individualQueryResult.first().getName() + ".DELETED_" + TimeUtils.getTime();
                ObjectMap updateParams = new ObjectMap()
                        .append(IndividualDBAdaptor.QueryParams.NAME.key(), newIndividualName)
                        .append(IndividualDBAdaptor.QueryParams.STATUS_NAME.key(), Status.DELETED);
                queryResult = individualDBAdaptor.update(individualId, updateParams);

                auditManager.recordDeletion(AuditRecord.Resource.individual, individualId, resourceId.getUser(),
                        individualQueryResult.first(), queryResult.first(), null, null);

            } catch (CatalogAuthorizationException e) {
                auditManager.recordDeletion(AuditRecord.Resource.individual, individualId, resourceId.getUser(), null, e.getMessage(),
                        null);

                queryResult = new QueryResult<>("Delete individual " + individualId);
                queryResult.setErrorMsg(e.getMessage());
            } catch (CatalogException e) {
                e.printStackTrace();
                queryResult = new QueryResult<>("Delete individual " + individualId);
                queryResult.setErrorMsg(e.getMessage());
            } finally {
                queryResultList.add(queryResult);
            }
        }

        return queryResultList;
    }

    public List<QueryResult<Individual>> delete(Query query, QueryOptions options, String sessionId) throws CatalogException, IOException {
        QueryOptions queryOptions = new QueryOptions(QueryOptions.INCLUDE, IndividualDBAdaptor.QueryParams.ID.key());
        QueryResult<Individual> individualQueryResult = individualDBAdaptor.get(query, queryOptions);
        List<Long> individualIds = individualQueryResult.getResult().stream().map(Individual::getId).collect(Collectors.toList());
        String individualStr = StringUtils.join(individualIds, ",");
        return delete(null, individualStr, options, sessionId);
    }

    @Override
    public QueryResult rank(String studyStr, Query query, String field, int numResults, boolean asc, String sessionId)
            throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        ParamUtils.checkObj(field, "field");
        ParamUtils.checkObj(sessionId, "sessionId");

        String userId = userManager.getUserId(sessionId);
        long studyId = studyManager.getId(userId, studyStr);

        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.VIEW_INDIVIDUALS);

        // TODO: In next release, we will have to check the count parameter from the queryOptions object.
        boolean count = true;
//        query.append(CatalogIndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
        QueryResult queryResult = null;
        if (count) {
            // We do not need to check for permissions when we show the count of files
            queryResult = individualDBAdaptor.rank(query, field, numResults, asc);
        }

        return ParamUtils.defaultObject(queryResult, QueryResult::new);
    }

    @Override
    public QueryResult groupBy(@Nullable String studyStr, Query query, List<String> fields, QueryOptions options, String sessionId)
            throws CatalogException {
        query = ParamUtils.defaultObject(query, Query::new);
        options = ParamUtils.defaultObject(options, QueryOptions::new);
        ParamUtils.checkObj(fields, "fields");

        String userId = userManager.getUserId(sessionId);
        long studyId = studyManager.getId(userId, studyStr);
        authorizationManager.checkStudyPermission(studyId, userId, StudyAclEntry.StudyPermissions.VIEW_INDIVIDUALS);

        // Add study id to the query
        query.put(IndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);

        // TODO: In next release, we will have to check the count parameter from the queryOptions object.
        boolean count = true;
//        query.append(CatalogIndividualDBAdaptor.QueryParams.STUDY_ID.key(), studyId);
        QueryResult queryResult = null;
        if (count) {
            // We do not need to check for permissions when we show the count of files
            queryResult = individualDBAdaptor.groupBy(query, fields, options);
        }

        return ParamUtils.defaultObject(queryResult, QueryResult::new);
    }


    @Override
    public QueryResult<AnnotationSet> createAnnotationSet(String id, @Nullable String studyStr, String variableSetId,
                                                          String annotationSetName, Map<String, Object> annotations,
                                                          Map<String, Object> attributes, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");
        ParamUtils.checkObj(annotations, "annotations");
        attributes = ParamUtils.defaultObject(attributes, HashMap<String, Object>::new);

        MyResourceId resource = catalogManager.getIndividualManager().getId(id, studyStr, sessionId);
        authorizationManager.checkIndividualPermission(resource.getStudyId(), resource.getResourceId(), resource.getUser(),
                IndividualAclEntry.IndividualPermissions.WRITE_ANNOTATIONS);
        MyResourceId variableSetResource = studyManager.getVariableSetId(variableSetId,
                Long.toString(resource.getStudyId()), sessionId);

        QueryResult<VariableSet> variableSet = studyDBAdaptor.getVariableSet(variableSetResource.getResourceId(), null,
                resource.getUser(), null);
        if (variableSet.getNumResults() == 0) {
            // Variable set must be confidential and the user does not have those permissions
            throw new CatalogAuthorizationException("Permission denied: User " + resource.getUser() + " cannot create annotations over "
                    + "that variable set");
        }

        QueryResult<AnnotationSet> annotationSet = createAnnotationSet(resource.getResourceId(), variableSet.first(), annotationSetName,
                annotations, studyManager.getCurrentRelease(resource.getStudyId()), attributes, individualDBAdaptor);

        auditManager.recordUpdate(AuditRecord.Resource.individual, resource.getResourceId(), resource.getUser(),
                new ObjectMap("annotationSets", annotationSet.first()), "annotate", null);

        return annotationSet;
    }

    @Override
    public QueryResult<AnnotationSet> getAllAnnotationSets(String id, @Nullable String studyStr, String sessionId) throws CatalogException {
        MyResourceId resource = commonGetAllInvidualSets(id, studyStr, sessionId);
        return individualDBAdaptor.getAnnotationSet(resource, null,
                StudyAclEntry.StudyPermissions.VIEW_INDIVIDUAL_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<ObjectMap> getAllAnnotationSetsAsMap(String id, @Nullable String studyStr, String sessionId)
            throws CatalogException {
        MyResourceId resource = commonGetAllInvidualSets(id, studyStr, sessionId);
        return individualDBAdaptor.getAnnotationSetAsMap(resource, null,
                StudyAclEntry.StudyPermissions.VIEW_INDIVIDUAL_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<AnnotationSet> getAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        MyResourceId resource = commonGetAnnotationSet(id, studyStr, annotationSetName, sessionId);
        return individualDBAdaptor.getAnnotationSet(resource, annotationSetName,
                StudyAclEntry.StudyPermissions.VIEW_INDIVIDUAL_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<ObjectMap> getAnnotationSetAsMap(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        MyResourceId resource = commonGetAnnotationSet(id, studyStr, annotationSetName, sessionId);
        return individualDBAdaptor.getAnnotationSetAsMap(resource, annotationSetName,
                StudyAclEntry.StudyPermissions.VIEW_INDIVIDUAL_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<AnnotationSet> updateAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, Map<String,
            Object> newAnnotations, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");
        ParamUtils.checkObj(newAnnotations, "newAnnotations");

        MyResourceId resource = getId(id, studyStr, sessionId);
        authorizationManager.checkIndividualPermission(resource.getStudyId(), resource.getResourceId(), resource.getUser(),
                IndividualAclEntry.IndividualPermissions.WRITE_ANNOTATIONS);

        // Update the annotation
        QueryResult<AnnotationSet> queryResult = updateAnnotationSet(resource, annotationSetName, newAnnotations, individualDBAdaptor);

        if (queryResult == null || queryResult.getNumResults() == 0) {
            throw new CatalogException("There was an error with the update");
        }

        AnnotationSet annotationSet = queryResult.first();

        // Audit the changes
        AnnotationSet annotationSetUpdate = new AnnotationSet(annotationSet.getName(), annotationSet.getVariableSetId(),
                newAnnotations.entrySet().stream()
                        .map(entry -> new Annotation(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toSet()), annotationSet.getCreationDate(), 1, null);
        auditManager.recordUpdate(AuditRecord.Resource.individual, resource.getResourceId(), resource.getUser(),
                new ObjectMap("annotationSets", Collections.singletonList(annotationSetUpdate)), "update annotation", null);

        return queryResult;
    }

    @Override
    public QueryResult<AnnotationSet> deleteAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        ParamUtils.checkParameter(annotationSetName, "annotationSetName");

        MyResourceId resource = getId(id, studyStr, sessionId);
        authorizationManager.checkIndividualPermission(resource.getStudyId(), resource.getResourceId(), resource.getUser(),
                IndividualAclEntry.IndividualPermissions.DELETE_ANNOTATIONS);

        QueryResult<AnnotationSet> annotationSet = individualDBAdaptor.getAnnotationSet(resource.getResourceId(), annotationSetName);
        if (annotationSet == null || annotationSet.getNumResults() == 0) {
            throw new CatalogException("Could not delete annotation set. The annotation set with name " + annotationSetName + " could not "
                    + "be found in the database.");
        }
        // We make this query because it will check the proper permissions in case the variable set is confidential
        studyDBAdaptor.getVariableSet(annotationSet.first().getVariableSetId(), new QueryOptions(), resource.getUser(), null);

        individualDBAdaptor.deleteAnnotationSet(resource.getResourceId(), annotationSetName);

        auditManager.recordDeletion(AuditRecord.Resource.individual, resource.getResourceId(), resource.getUser(),
                new ObjectMap("annotationSets", Collections.singletonList(annotationSet.first())), "delete annotation", null);

        return annotationSet;
    }

    @Override
    public QueryResult<ObjectMap> searchAnnotationSetAsMap(String id, @Nullable String studyStr, String variableSetStr,
                                                           @Nullable String annotation, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        MyResourceId resource = getId(id, studyStr, sessionId);
//        authorizationManager.checkIndividualPermission(resource.getStudyId(), resource.getResourceId(), resource.getUser(),
//                IndividualAclEntry.IndividualPermissions.VIEW_ANNOTATIONS);

        long variableSetId = -1;
        if (StringUtils.isNotEmpty(variableSetStr)) {
            variableSetId = studyManager.getVariableSetId(variableSetStr, Long.toString(resource.getStudyId()),
                    sessionId).getResourceId();
        }

        return individualDBAdaptor.searchAnnotationSetAsMap(resource, variableSetId, annotation,
                StudyAclEntry.StudyPermissions.VIEW_INDIVIDUAL_ANNOTATIONS.toString());
    }

    @Override
    public QueryResult<AnnotationSet> searchAnnotationSet(String id, @Nullable String studyStr, String variableSetStr,
                                                          @Nullable String annotation, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");

        MyResourceId resource = getId(id, studyStr, sessionId);
//        authorizationManager.checkIndividualPermission(resource.getStudyId(), resource.getResourceId(), resource.getUser(),
//                IndividualAclEntry.IndividualPermissions.VIEW_ANNOTATIONS);

        long variableSetId = -1;
        if (StringUtils.isNotEmpty(variableSetStr)) {
            variableSetId = studyManager.getVariableSetId(variableSetStr, Long.toString(resource.getStudyId()),
                    sessionId).getResourceId();
        }

        return individualDBAdaptor.searchAnnotationSet(resource, variableSetId, annotation,
                StudyAclEntry.StudyPermissions.VIEW_INDIVIDUAL_ANNOTATIONS.toString());
    }


    private MyResourceId commonGetAllInvidualSets(String id, @Nullable String studyStr, String sessionId) throws CatalogException {
        ParamUtils.checkParameter(id, "id");
        return getId(id, studyStr, sessionId);
//        authorizationManager.checkIndividualPermission(resource.getStudyId(), resource.getResourceId(), resource.getUser(),
//                IndividualAclEntry.IndividualPermissions.VIEW_ANNOTATIONS);
//        return resource.getResourceId();
    }


    private MyResourceId commonGetAnnotationSet(String id, @Nullable String studyStr, String annotationSetName, String sessionId)
            throws CatalogException {
        ParamUtils.checkAlias(annotationSetName, "annotationSetName", configuration.getCatalog().getOffset());
        return getId(id, studyStr, sessionId);
//        authorizationManager.checkIndividualPermission(resource.getStudyId(), resource.getResourceId(), resource.getUser(),
//                IndividualAclEntry.IndividualPermissions.VIEW_ANNOTATIONS);
//        return resource.getResourceId();
    }


    // **************************   ACLs  ******************************** //

    public List<QueryResult<IndividualAclEntry>> getAcls(String studyStr, String individualStr, String sessionId) throws CatalogException {
        MyResourceIds resource = getIds(individualStr, studyStr, sessionId);

        List<QueryResult<IndividualAclEntry>> individualAclList = new ArrayList<>(resource.getResourceIds().size());
        for (Long individualId : resource.getResourceIds()) {
            QueryResult<IndividualAclEntry> allIndividualAcls =
                    authorizationManager.getAllIndividualAcls(resource.getStudyId(), individualId, resource.getUser());
            allIndividualAcls.setId(String.valueOf(individualId));
            individualAclList.add(allIndividualAcls);
        }

        return individualAclList;
    }

    public List<QueryResult<IndividualAclEntry>> getAcl(String studyStr, String individualStr, String member, String sessionId)
            throws CatalogException {
        ParamUtils.checkObj(member, "member");

        MyResourceIds resource = getIds(individualStr, studyStr, sessionId);
        checkMembers(resource.getStudyId(), Arrays.asList(member));

        List<QueryResult<IndividualAclEntry>> individualAclList = new ArrayList<>(resource.getResourceIds().size());
        for (Long individualId : resource.getResourceIds()) {
            QueryResult<IndividualAclEntry> allIndividualAcls =
                    authorizationManager.getIndividualAcl(resource.getStudyId(), individualId, resource.getUser(), member);
            allIndividualAcls.setId(String.valueOf(individualId));
            individualAclList.add(allIndividualAcls);
        }

        return individualAclList;
    }

    public List<QueryResult<IndividualAclEntry>> updateAcl(String studyStr, String individualStr, String memberIds,
                                                           Individual.IndividualAclParams aclParams, String sessionId)
            throws CatalogException {
        int count = 0;
        count += StringUtils.isNotEmpty(individualStr) ? 1 : 0;
        count += StringUtils.isNotEmpty(aclParams.getSample()) ? 1 : 0;

        if (count > 1) {
            throw new CatalogException("Update ACL: Only one of these parameters are allowed: individual or sample per query.");
        } else if (count == 0) {
            throw new CatalogException("Update ACL: At least one of these parameters should be provided: individual or sample");
        }

        if (aclParams.getAction() == null) {
            throw new CatalogException("Invalid action found. Please choose a valid action to be performed.");
        }

        List<String> permissions = Collections.emptyList();
        if (StringUtils.isNotEmpty(aclParams.getPermissions())) {
            permissions = Arrays.asList(aclParams.getPermissions().trim().replaceAll("\\s", "").split(","));
            checkPermissions(permissions, IndividualAclEntry.IndividualPermissions::valueOf);
        }

        if (StringUtils.isNotEmpty(aclParams.getSample())) {
            // Obtain the sample ids
            MyResourceIds ids = catalogManager.getSampleManager().getIds(aclParams.getSample(), studyStr, sessionId);

            Query query = new Query(SampleDBAdaptor.QueryParams.ID.key(), ids.getResourceIds());
            QueryOptions options = new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key());
            QueryResult<Sample> sampleQueryResult = catalogManager.getSampleManager().get(ids.getStudyId(), query, options, sessionId);

            Set<Long> individualSet = sampleQueryResult.getResult().stream().map(sample -> sample.getIndividual().getId())
                    .collect(Collectors.toSet());
            individualStr = StringUtils.join(individualSet, ",");

            studyStr = Long.toString(ids.getStudyId());
        }

        // Obtain the resource ids
        MyResourceIds resourceIds = getIds(individualStr, studyStr, sessionId);

        // Check the user has the permissions needed to change permissions over those individuals
        for (Long individualId : resourceIds.getResourceIds()) {
            authorizationManager.checkIndividualPermission(resourceIds.getStudyId(), individualId, resourceIds.getUser(),
                    IndividualAclEntry.IndividualPermissions.SHARE);
        }

        // Validate that the members are actually valid members
        List<String> members;
        if (memberIds != null && !memberIds.isEmpty()) {
            members = Arrays.asList(memberIds.split(","));
        } else {
            members = Collections.emptyList();
        }
        checkMembers(resourceIds.getStudyId(), members);
//        studyManager.membersHavePermissionsInStudy(resourceIds.getStudyId(), members);

        String collectionName = MongoDBAdaptorFactory.INDIVIDUAL_COLLECTION;

        List<QueryResult<IndividualAclEntry>> queryResults;
        switch (aclParams.getAction()) {
            case SET:
                queryResults = authorizationManager.setAcls(resourceIds.getStudyId(), resourceIds.getResourceIds(), members, permissions,
                        collectionName);
                if (aclParams.isPropagate()) {
                    List<Long> sampleIds = getSamplesFromIndividuals(resourceIds);
                    if (sampleIds.size() > 0) {
                        Sample.SampleAclParams sampleAclParams = new Sample.SampleAclParams(aclParams.getPermissions(),
                                AclParams.Action.SET, null, null, null);
                        catalogManager.getSampleManager().updateAcl(studyStr, StringUtils.join(sampleIds, ","), memberIds, sampleAclParams,
                                sessionId);
                    }
                }
                break;
            case ADD:
                queryResults = authorizationManager.addAcls(resourceIds.getStudyId(), resourceIds.getResourceIds(), members, permissions,
                        collectionName);
                if (aclParams.isPropagate()) {
                    List<Long> sampleIds = getSamplesFromIndividuals(resourceIds);
                    if (sampleIds.size() > 0) {
                        Sample.SampleAclParams sampleAclParams = new Sample.SampleAclParams(aclParams.getPermissions(),
                                AclParams.Action.ADD, null, null, null);
                        catalogManager.getSampleManager().updateAcl(studyStr, StringUtils.join(sampleIds, ","), memberIds, sampleAclParams,
                                sessionId);
                    }
                }
                break;
            case REMOVE:
                queryResults = authorizationManager.removeAcls(resourceIds.getResourceIds(), members, permissions, collectionName);
                if (aclParams.isPropagate()) {
                    List<Long> sampleIds = getSamplesFromIndividuals(resourceIds);
                    if (CollectionUtils.isNotEmpty(sampleIds)) {
                        Sample.SampleAclParams sampleAclParams = new Sample.SampleAclParams(aclParams.getPermissions(),
                                AclParams.Action.REMOVE, null, null, null);
                        catalogManager.getSampleManager().updateAcl(studyStr, StringUtils.join(sampleIds, ","), memberIds,
                                sampleAclParams, sessionId);
                    }
                }
                break;
            case RESET:
                queryResults = authorizationManager.removeAcls(resourceIds.getResourceIds(), members, null, collectionName);
                if (aclParams.isPropagate()) {
                    List<Long> sampleIds = getSamplesFromIndividuals(resourceIds);
                    if (CollectionUtils.isNotEmpty(sampleIds)) {
                        Sample.SampleAclParams sampleAclParams = new Sample.SampleAclParams(aclParams.getPermissions(),
                                AclParams.Action.RESET, null, null, null);
                        catalogManager.getSampleManager().updateAcl(studyStr, StringUtils.join(sampleIds, ","), memberIds,
                                sampleAclParams, sessionId);
                    }
                }
                break;
            default:
                throw new CatalogException("Unexpected error occurred. No valid action found.");
        }

        return queryResults;
    }


    // **************************   Private methods  ******************************** //

    private List<Long> getSamplesFromIndividuals(MyResourceIds resourceIds) throws CatalogDBException {
        // Look for all the samples belonging to the individual
        Query query = new Query()
                .append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), resourceIds.getStudyId())
                .append(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key(), resourceIds.getResourceIds());

        QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query,
                new QueryOptions(QueryOptions.INCLUDE, SampleDBAdaptor.QueryParams.ID.key()));

        return sampleQueryResult.getResult().stream().map(Sample::getId).collect(Collectors.toList());
    }

    // Checks if father or mother are in query and transforms them into father.id and mother.id respectively
    private void fixQuery(long studyId, Query query, String sessionId) throws CatalogException {
        if (StringUtils.isNotEmpty(query.getString(IndividualDBAdaptor.QueryParams.FATHER.key()))) {
            MyResourceId resource =
                    getId(query.getString(IndividualDBAdaptor.QueryParams.FATHER.key()), String.valueOf(studyId), sessionId);
            query.remove(IndividualDBAdaptor.QueryParams.FATHER.key());
            query.append(IndividualDBAdaptor.QueryParams.FATHER_ID.key(), resource.getResourceId());
        }
        if (StringUtils.isNotEmpty(query.getString(IndividualDBAdaptor.QueryParams.MOTHER.key()))) {
            MyResourceId resource =
                    getId(query.getString(IndividualDBAdaptor.QueryParams.MOTHER.key()), String.valueOf(studyId), sessionId);
            query.remove(IndividualDBAdaptor.QueryParams.MOTHER.key());
            query.append(IndividualDBAdaptor.QueryParams.MOTHER_ID.key(), resource.getResourceId());
        }
    }

    private void addSampleInformation(QueryResult<Individual> individualQueryResult, long studyId, String userId) {
        if (individualQueryResult.getNumResults() == 0) {
            return;
        }
        List<Long> individualIds = individualQueryResult.getResult().stream().map(Individual::getId).collect(Collectors.toList());
        if (individualIds.isEmpty()) {
            return;
        }
        try {
            Query query = new Query()
                    .append(SampleDBAdaptor.QueryParams.STUDY_ID.key(), studyId)
                    .append(SampleDBAdaptor.QueryParams.INDIVIDUAL_ID.key(), individualIds);
            QueryResult<Sample> sampleQueryResult = sampleDBAdaptor.get(query, QueryOptions.empty(), userId);

            Map<String, List<Sample>> individualSamplesMap = new HashMap<>();
            // Initialise the map
            for (Long individualId : individualIds) {
                individualSamplesMap.put(String.valueOf(individualId), new ArrayList<>());
            }
            // Assign samples to the map
            for (Sample sample : sampleQueryResult.getResult()) {
                String individualId = String.valueOf(((Map<String, Object>) sample.getAttributes().get("individual")).get("id"));
                individualSamplesMap.get(individualId).add(sample);
            }

            // Fill the individual queryResult with the list of samples obtained
            for (Individual individual : individualQueryResult.getResult()) {
                individual.setSamples(individualSamplesMap.get(String.valueOf(individual.getId())));
            }

        } catch (CatalogException e) {
            logger.warn("Could not retrieve sample information to complete individual object, {}", e.getMessage(), e);
            individualQueryResult.setWarningMsg("Could not retrieve sample information to complete individual object" + e.getMessage());
        }
    }

}
