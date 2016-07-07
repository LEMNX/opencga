package org.opencb.opencga.catalog.auth.authorization;

import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.*;
import org.opencb.opencga.catalog.models.acls.*;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Created by pfurio on 12/05/16.
 */
public interface AuthorizationManager {

    String FILTER_ROUTE_STUDIES = "projects.studies.";
    String FILTER_ROUTE_COHORTS = "projects.studies.cohorts.";
    String FILTER_ROUTE_DATASETS = "projects.studies.datasets.";
    String FILTER_ROUTE_INDIVIDUALS = "projects.studies.individuals.";
    String FILTER_ROUTE_SAMPLES = "projects.studies.samples.";
    String FILTER_ROUTE_FILES = "projects.studies.files.";
    String FILTER_ROUTE_JOBS = "projects.studies.jobs.";

    String ROLE_ADMIN = "admin";
    String ROLE_ANALYST = "analyst";
    String ROLE_LOCKED = "locked";

    @Deprecated
    String ADMINS_ROLE = "@admins";
    @Deprecated
    String DATA_MANAGERS_ROLE = "dataManagers";
    @Deprecated
    String MEMBERS_ROLE = "@members";

    String OTHER_USERS_ID = "*";

    static EnumSet<StudyAcl.StudyPermissions> getAdminAcls() {
        return EnumSet.allOf(StudyAcl.StudyPermissions.class);
    }

    static EnumSet<StudyAcl.StudyPermissions> getAnalystAcls() {
        return EnumSet.of(StudyAcl.StudyPermissions.VIEW_STUDY,
                StudyAcl.StudyPermissions.UPDATE_STUDY, StudyAcl.StudyPermissions.CREATE_VARIABLE_SET,
                StudyAcl.StudyPermissions.VIEW_VARIABLE_SET, StudyAcl.StudyPermissions.UPDATE_VARIABLE_SET,
                StudyAcl.StudyPermissions.CREATE_FILES, StudyAcl.StudyPermissions.VIEW_FILE_HEADERS,
                StudyAcl.StudyPermissions.VIEW_FILE_CONTENTS, StudyAcl.StudyPermissions.VIEW_FILES,
                StudyAcl.StudyPermissions.UPDATE_FILES, StudyAcl.StudyPermissions.DOWNLOAD_FILES,
                StudyAcl.StudyPermissions.CREATE_JOBS, StudyAcl.StudyPermissions.VIEW_JOBS, StudyAcl.StudyPermissions.UPDATE_JOBS,
                StudyAcl.StudyPermissions.CREATE_SAMPLES, StudyAcl.StudyPermissions.VIEW_SAMPLES, StudyAcl.StudyPermissions.UPDATE_SAMPLES,
                StudyAcl.StudyPermissions.CREATE_SAMPLE_ANNOTATIONS, StudyAcl.StudyPermissions.VIEW_SAMPLE_ANNOTATIONS,
                StudyAcl.StudyPermissions.UPDATE_SAMPLE_ANNOTATIONS, StudyAcl.StudyPermissions.CREATE_INDIVIDUALS,
                StudyAcl.StudyPermissions.VIEW_INDIVIDUALS, StudyAcl.StudyPermissions.UPDATE_INDIVIDUALS,
                StudyAcl.StudyPermissions.CREATE_INDIVIDUAL_ANNOTATIONS, StudyAcl.StudyPermissions.VIEW_INDIVIDUAL_ANNOTATIONS,
                StudyAcl.StudyPermissions.UPDATE_INDIVIDUAL_ANNOTATIONS, StudyAcl.StudyPermissions.CREATE_COHORTS,
                StudyAcl.StudyPermissions.VIEW_COHORTS, StudyAcl.StudyPermissions.UPDATE_COHORTS,
                StudyAcl.StudyPermissions.CREATE_COHORT_ANNOTATIONS, StudyAcl.StudyPermissions.VIEW_COHORT_ANNOTATIONS,
                StudyAcl.StudyPermissions.UPDATE_COHORT_ANNOTATIONS, StudyAcl.StudyPermissions.CREATE_DATASETS,
                StudyAcl.StudyPermissions.VIEW_DATASETS, StudyAcl.StudyPermissions.UPDATE_DATASETS,
                StudyAcl.StudyPermissions.CREATE_PANELS, StudyAcl.StudyPermissions.VIEW_PANELS, StudyAcl.StudyPermissions.UPDATE_PANELS);
    }

    static EnumSet<StudyAcl.StudyPermissions> getLockedAcls() {
        return EnumSet.noneOf(StudyAcl.StudyPermissions.class);
    }

//    /**
//     * Get the default Acls for the default roles.
//     *
//     * admins : Full permissions
//     * dataManagers: Full data permissions. No study permissions
//     * members: Just launch jobs permission.
//     *
//     * @param adminUsers Users to add to the admin group by default.
//     * @return List<Role>
//     */
//    static List<StudyAcl> getDefaultAcls(Collection<String> adminUsers) {
//        List<StudyAcl> studyAcls = new ArrayList<>(3);
//        studyAcls.add(new StudyAcl(ROLE_ADMIN, new ArrayList<>(adminUsers), EnumSet.allOf(StudyAcl.StudyPermissions.class)));
//        studyAcls.add(new StudyAcl(ROLE_ANALYST, Collections.emptyList(), EnumSet.of(StudyAcl.StudyPermissions.VIEW_STUDY,
//                StudyAcl.StudyPermissions.UPDATE_STUDY, StudyAcl.StudyPermissions.CREATE_VARIABLE_SET,
//                StudyAcl.StudyPermissions.VIEW_VARIABLE_SET, StudyAcl.StudyPermissions.UPDATE_VARIABLE_SET,
//                StudyAcl.StudyPermissions.CREATE_FILES, StudyAcl.StudyPermissions.VIEW_FILE_HEADERS,
//                StudyAcl.StudyPermissions.VIEW_FILE_CONTENTS, StudyAcl.StudyPermissions.VIEW_FILES,
//                StudyAcl.StudyPermissions.UPDATE_FILES, StudyAcl.StudyPermissions.DOWNLOAD_FILES,
//                StudyAcl.StudyPermissions.CREATE_JOBS, StudyAcl.StudyPermissions.VIEW_JOBS, StudyAcl.StudyPermissions.UPDATE_JOBS,
//                StudyAcl.StudyPermissions.CREATE_SAMPLES, StudyAcl.StudyPermissions.VIEW_SAMPLES,
// StudyAcl.StudyPermissions.UPDATE_SAMPLES,
//                StudyAcl.StudyPermissions.CREATE_SAMPLE_ANNOTATIONS, StudyAcl.StudyPermissions.VIEW_SAMPLE_ANNOTATIONS,
//                StudyAcl.StudyPermissions.UPDATE_SAMPLE_ANNOTATIONS, StudyAcl.StudyPermissions.CREATE_INDIVIDUALS,
//                StudyAcl.StudyPermissions.VIEW_INDIVIDUALS, StudyAcl.StudyPermissions.UPDATE_INDIVIDUALS,
//                StudyAcl.StudyPermissions.CREATE_INDIVIDUAL_ANNOTATIONS, StudyAcl.StudyPermissions.VIEW_INDIVIDUAL_ANNOTATIONS,
//                StudyAcl.StudyPermissions.UPDATE_INDIVIDUAL_ANNOTATIONS, StudyAcl.StudyPermissions.CREATE_COHORTS,
//                StudyAcl.StudyPermissions.VIEW_COHORTS, StudyAcl.StudyPermissions.UPDATE_COHORTS,
//                StudyAcl.StudyPermissions.CREATE_COHORT_ANNOTATIONS, StudyAcl.StudyPermissions.VIEW_COHORT_ANNOTATIONS,
//                StudyAcl.StudyPermissions.UPDATE_COHORT_ANNOTATIONS, StudyAcl.StudyPermissions.CREATE_DATASETS,
//                StudyAcl.StudyPermissions.VIEW_DATASETS, StudyAcl.StudyPermissions.UPDATE_DATASETS,
//                StudyAcl.StudyPermissions.CREATE_PANELS, StudyAcl.StudyPermissions.VIEW_PANELS,
// StudyAcl.StudyPermissions.UPDATE_PANELS)));
//        studyAcls.add(new StudyAcl(ROLE_LOCKED, Collections.emptyList(), EnumSet.noneOf(StudyAcl.StudyPermissions.class)));
//        // TODO: Add all the default roles and permissions.
//        return studyAcls;
//    }

    void checkProjectPermission(long projectId, String userId, StudyAcl.StudyPermissions permission) throws CatalogException;

    void checkStudyPermission(long studyId, String userId, StudyAcl.StudyPermissions permission) throws CatalogException;

    void checkStudyPermission(long studyId, String userId, StudyAcl.StudyPermissions permission, String message) throws CatalogException;

    void checkFilePermission(long fileId, String userId, FileAcl.FilePermissions permission) throws CatalogException;

    void checkSamplePermission(long sampleId, String userId, SampleAcl.SamplePermissions permission) throws CatalogException;

    void checkIndividualPermission(long individualId, String userId, IndividualAcl.IndividualPermissions permission)
            throws CatalogException;

    void checkJobPermission(long jobId, String userId, JobAcl.JobPermissions permission) throws CatalogException;

    void checkCohortPermission(long cohortId, String userId, CohortAcl.CohortPermissions permission) throws CatalogException;

    void checkDatasetPermission(long datasetId, String userId, DatasetAcl.DatasetPermissions permission) throws CatalogException;

    void checkDiseasePanelPermission(long panelId, String userId, DiseasePanelAcl.DiseasePanelPermissions permission)
            throws CatalogException;

    //User.Role getUserRole(String userId) throws CatalogException;

    /**
     * Set the permissions given for all the users and file ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param fileIds List of file ids.
     * @param userIds Comma separated list of user ids which the files will be shared with.
     * @param permissions List of file permissions.
     * @param override Boolean parameter indicating whether to set the Acl when the members already had other Acl set. In that case, the old
     *                 Acl will be removed and the new one will be set. Otherwise, an exception will be raised.
     * @return A queryResult containing the FileAcl applied to the different file ids.
     * @throws CatalogException when the user ordering the action does not have permission to share the files.
     */
    @Deprecated
    QueryResult<FileAcl> setFilePermissions(String userId, List<Long> fileIds, String userIds, List<String> permissions, boolean override)
            throws CatalogException;

    /**
     * Remove the permissions given for all the users in the file ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param fileIds list of file ids.
     * @param userIds Comma separated list of user ids from whom the permissions will be removed.
     * @param permissions Comma separated list of permissions to be removed. If the list is empty, it will remove all the permissions.
     * @throws CatalogException when the user ordering the action does not have permission to share the files.
     */
    @Deprecated
    void unsetFilePermissions(String userId, List<Long> fileIds, String userIds, List<String> permissions) throws CatalogException;

    /**
     * Set the permissions given for all the users and sample ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param sampleIds list of sample ids.
     * @param userIds Comma separated list of user ids which the samples will be shared with.
     * @param permissions List of sample permissions.
     * @param override Boolean parameter indicating whether to set the Acl when the members already had other Acl set. In that case, the old
     *                 Acl will be removed and the new one will be set. Otherwise, an exception will be raised.
     * @return A queryResult containing the SampleAcl applied to the different sample ids.
     * @throws CatalogException when the user ordering the action does not have permission to share the samples.
     */
    @Deprecated
    QueryResult<SampleAcl> setSamplePermissions(String userId, List<Long> sampleIds, String userIds, List<String> permissions,
                                                boolean override) throws CatalogException;

    /**
     * Remove the permissions given for all the users in the sample ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param sampleIds list of sample ids.
     * @param userIds Comma separated list of user ids from whom the permissions will be removed.
     * @param permissions Comma separated list of permissions to be removed. If the list is empty, it will remove all the permissions.
     * @throws CatalogException when the user ordering the action does not have permission to share the samples.
     */
    @Deprecated
    void unsetSamplePermissions(String userId, List<Long> sampleIds, String userIds, List<String> permissions) throws CatalogException;

    //@Deprecated
    /**
     * Set the permissions given for all the users and cohort ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param cohortIds Comma separated list of cohort ids.
     * @param userIds Comma separated list of user ids which the cohort will be shared with.
     * @param permissions List of cohort permissions.
     * @param override Boolean parameter indicating whether to set the Acl when the members already had other Acl set. In that case, the old
     *                 Acl will be removed and the new one will be set. Otherwise, an exception will be raised.
     * @return A queryResult containing the CohortAcl applied to the different cohort ids.
     * @throws CatalogException when the user ordering the action does not have permission to share the cohorts.
     */
    @Deprecated
    QueryResult<CohortAcl> setCohortPermissions(String userId, List<Long> cohortIds, String userIds, List<String> permissions,
                                                boolean override) throws CatalogException;


/*
    /**
     * Set the permissions given for all the users and cohort ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param cohortIds Comma separated list of cohort ids.
     * @param userIds Comma separated list of user ids which the cohort will be shared with.
     * @param permissions List of cohort permissions.
     * @param override Boolean parameter indicating whether to set the Acl when the members already had other Acl set. In that case, the old
     *                 Acl will be removed and the new one will be set. Otherwise, an exception will be raised.
     * @return A queryResult containing the CohortAcl applied to the different cohort ids.
     * @throws CatalogException when the user ordering the action does not have permission to share the cohorts.
     */
   /* QueryResult<CohortAcl> setCohortPermissions(String userId, Integer cohortId, String member,
                                                List<String> permissions) throws CatalogException;*/

  /*  /**
     * Set the permissions given for all the users and cohort ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param cohortId Cohort id.
     * @return A queryResult containing the CohortAcl existing in a cohort.
     * @throws CatalogException when the user ordering the action does not have permission to share the cohorts.
     */
   // QueryResult<CohortAcl> getCohortPermissions(String userId, Integer cohortId) throws CatalogException;


    /**
     * Remove the permissions given for all the users in the cohort ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param cohortIds Comma separated list of cohort ids.
     * @param userIds Comma separated list of user ids from whom the permissions will be removed.
     * @param permissions Comma separated list of permissions to be removed. If the list is empty, it will remove all the permissions.
     * @throws CatalogException when the user ordering the action does not have permission to share the cohorts.
     */
    @Deprecated
    void unsetCohortPermissions(String userId, List<Long> cohortIds, String userIds, List<String> permissions) throws CatalogException;

    /**
     * Set the permissions given for all the users and individual ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param individualIds Comma separated list of individual ids.
     * @param userIds Comma separated list of user ids which the individual will be shared with.
     * @param permissions List of individual permissions.
     * @param override Boolean parameter indicating whether to set the Acl when the members already had other Acl set. In that case, the old
     *                 Acl will be removed and the new one will be set. Otherwise, an exception will be raised.
     * @return A queryResult containing the IndividualAcl applied to the different individual ids.
     * @throws CatalogException when the user ordering the action does not have permission to share the individuals.
     */
    @Deprecated
    QueryResult<IndividualAcl> setIndividualPermissions(String userId, List<Long> individualIds, String userIds, List<String> permissions,
                                                        boolean override) throws CatalogException;

    /**
     * Remove the permissions given for all the users in the individual ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param individualIds Comma separated list of individual ids.
     * @param userIds Comma separated list of user ids from whom the permissions will be removed.
     * @param permissions Comma separated list of permissions to be removed. If the list is empty, it will remove all the permissions.
     * @throws CatalogException when the user ordering the action does not have permission to share the individuals.
     */
    @Deprecated
    void unsetIndividualPermissions(String userId, List<Long> individualIds, String userIds, List<String> permissions)
            throws CatalogException;

    /**
     * Set the permissions given for all the users and job ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param jobIds Comma separated list of job ids.
     * @param userIds Comma separated list of user ids which the job will be shared with.
     * @param permissions List of job permissions.
     * @param override Boolean parameter indicating whether to set the Acl when the members already had other Acl set. In that case, the old
     *                 Acl will be removed and the new one will be set. Otherwise, an exception will be raised.
     * @return A queryResult containing the JobAcl applied to the different job ids.
     * @throws CatalogException when the user ordering the action does not have permission to share the jobs.
     */
    @Deprecated
    QueryResult<JobAcl> setJobPermissions(String userId, List<Long> jobIds, String userIds, List<String> permissions, boolean override)
            throws CatalogException;

    /**
     * Remove the permissions given for all the users in the job ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param jobIds Comma separated list of job ids.
     * @param userIds Comma separated list of user ids from whom the permissions will be removed.
     * @param permissions Comma separated list of permissions to be removed. If the list is empty, it will remove all the permissions.
     * @throws CatalogException when the user ordering the action does not have permission to share the jobs.
     */
    @Deprecated
    void unsetJobPermissions(String userId, List<Long> jobIds, String userIds, List<String> permissions) throws CatalogException;

    /**
     * Set the permissions given for all the users and dataset ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param datasetIds Comma separated list of dataset ids.
     * @param userIds Comma separated list of user ids which the dataset will be shared with.
     * @param permissions List of dataset permissions.
     * @param override Boolean parameter indicating whether to set the Acl when the members already had other Acl set. In that case, the old
     *                 Acl will be removed and the new one will be set. Otherwise, an exception will be raised.
     * @return A queryResult containing the DatasetAcl applied to the different dataset ids.
     * @throws CatalogException when the user ordering the action does not have permission to share the datasets.
     */
    @Deprecated
    QueryResult<DatasetAcl> setDatasetPermissions(String userId, List<Long> datasetIds, String userIds, List<String> permissions,
                                                  boolean override) throws CatalogException;

    /**
     * Remove the permissions given for all the users in the dataset ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param datasetIds Comma separated list of dataset ids.
     * @param userIds Comma separated list of user ids from whom the permissions will be removed.
     * @param permissions Comma separated list of permissions to be removed. If the list is empty, it will remove all the permissions.
     * @throws CatalogException when the user ordering the action does not have permission to share the datasets.
     */
    @Deprecated
    void unsetDatasetPermissions(String userId, List<Long> datasetIds, String userIds, List<String> permissions) throws CatalogException;

    /**
     * Set the permissions given for all the users and panel ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param panelIds Comma separated list of panel ids.
     * @param userIds Comma separated list of user ids which the panel will be shared with.
     * @param permissions List of panel permissions.
     * @param override Boolean parameter indicating whether to set the Acl when the members already had other Acl set. In that case, the old
     *                 Acl will be removed and the new one will be set. Otherwise, an exception will be raised.
     * @return A queryResult containing the DiseasePanelAcl applied to the different panel ids.
     * @throws CatalogException when the user ordering the action does not have permission to share the panels.
     */
    @Deprecated
    QueryResult<DiseasePanelAcl> setDiseasePanelPermissions(String userId, List<Long> panelIds, String userIds, List<String> permissions,
                                                            boolean override) throws CatalogException;

    /**
     * Remove the permissions given for all the users in the panel ids given.
     *
     * @param userId User id of the user that is performing the action.
     * @param panelIds Comma separated list of panel ids.
     * @param userIds Comma separated list of user ids from whom the permissions will be removed.
     * @param permissions Comma separated list of permissions to be removed. If the list is empty, it will remove all the permissions.
     * @throws CatalogException when the user ordering the action does not have permission to share the panels.
     */
    @Deprecated
    void unsetDiseasePanelPermissions(String userId, List<Long> panelIds, String userIds, List<String> permissions) throws CatalogException;

    /**
     * Removes from the list the projects that the user can not read.
     * From the remaining projects, filters the studies.
     *
     * @param userId  UserId.
     * @param projects Project list.
     * @throws CatalogException CatalogException
     */
    void filterProjects(String userId, List<Project> projects) throws CatalogException;

    /**
     * Removes from the list the studies that the user can not read.
     * From the remaining studies, filters the files.
     *
     * @param userId  UserId.
     * @param studies Studies list.
     * @throws CatalogException CatalogException
     */
    void filterStudies(String userId, List<Study> studies) throws CatalogException;

    /**
     * Removes from the list the files that the user can not read.
     *
     * @param userId  UserId
     * @param studyId StudyId
     * @param files   Files list
     * @throws CatalogException CatalogException
     */
    void filterFiles(String userId, long studyId, List<File> files) throws CatalogException;

    /**
     * Removes from the list the samples that the user can not read.
     *
     * @param userId  UserId
     * @param studyId StudyId
     * @param samples Samples
     * @throws CatalogException CatalogException
     */
    void filterSamples(String userId, long studyId, List<Sample> samples) throws CatalogException;

    /**
     * Removes from the list the individuals that the user can not read.
     *
     * @param userId  UserId
     * @param studyId StudyId
     * @param individuals Individuals
     * @throws CatalogException CatalogException
     */
    void filterIndividuals(String userId, long studyId, List<Individual> individuals) throws CatalogException;

    /**
     * Removes from the list the cohorts that the user can not read.
     *
     * @param userId  UserId.
     * @param studyId StudyId.
     * @param cohorts Cohorts.
     * @throws CatalogException CatalogException.
     */
    void filterCohorts(String userId, long studyId, List<Cohort> cohorts) throws CatalogException;

    /**
     * Removes from the list the jobs that the user can not read.
     *
     * @param userId  UserId.
     * @param studyId StudyId.
     * @param jobs Jobs.
     * @throws CatalogException CatalogException.
     */
    void filterJobs(String userId, long studyId, List<Job> jobs) throws CatalogException;

    /**
     * Removes from the list the datasets that the user can not read.
     *
     * @param userId  UserId.
     * @param studyId StudyId.
     * @param datasets datasets.
     * @throws CatalogException CatalogException.
     */
    void filterDatasets(String userId, long studyId, List<Dataset> datasets) throws CatalogException;

    @Deprecated
    /**
     * Adds the members to the groupId specified.
     *
     * @param userId User id of the user ordering the action.
     * @param studyId Study id under which the newUserId will be added to the group.
     * @param groupId Group id where the userId wants to add the newUser.
     * @param members List of user ids that will be added to the group.
     * @return a queryResult containing the group created.
     * @throws CatalogException when the userId does not have the proper permissions to add other users to groups.
     */
    QueryResult<Group> addUsersToGroup(String userId, long studyId, String groupId, List<String> members) throws CatalogException;
    @Deprecated
    default QueryResult<Group> addUsersToGroup(String userId, long studyId, String groupId, String members) throws CatalogException {
        return addUsersToGroup(userId, studyId, groupId, Arrays.asList(members.split(",")));
    }

    @Deprecated
    /**
     * Removes the members from the groupId specified.
     *
     * @param userId User id of the user ordering the action.
     * @param studyId Study id under which the oldUserId will be removed from the groupId.
     * @param groupId Group id where the userId wants to remove the oldUserId from.
     * @param members List of user ids that will be taken out from the group.
     * @throws CatalogException when the userId does not have the proper permissions to remove other users from groups.
     */
    void removeUsersFromGroup(String userId, long studyId, String groupId, List<String> members) throws CatalogException;
    @Deprecated
    default void removeUsersFromGroup(String userId, long studyId, String groupId, String members) throws CatalogException {
        removeUsersFromGroup(userId, studyId, groupId, Arrays.asList(members.split(",")));
    }

    /**
     * Adds the list of members to the roleId specified.
     *
     * @param userId User id of the user ordering the action.
     * @param studyId Study id under which the members will be added to the role.
     * @param members List of member ids (users and/or groups).
     * @param permissions List of permissions to be added to the members. If a template is provided, the permissions present here will be
     *                    added to the list of permissions present in the template.
     * @param template Template to be used to get the default permissions from. Might be null.
     * @return a queryResult containing the complete studyAcl where the members have been added to.
     * @throws CatalogException when the userId does not have the proper permissions or the members or the roleId do not exist.
     */
    QueryResult<StudyAcl> createStudyAcls(String userId, long studyId, List<String> members, List<String> permissions,
                                          @Nullable String template) throws CatalogException;
    default QueryResult<StudyAcl> createStudyAcls(String userId, long studyId, String members, String permissions,
                                                  @Nullable String template) throws CatalogException {
        List<String> permissionList;
        if (permissions != null && !permissions.isEmpty()) {
            permissionList = Arrays.asList(permissions.split(","));
        } else {
            permissionList = Collections.emptyList();
        }

        List<String> memberList;
        if (members != null && !members.isEmpty()) {
            memberList = Arrays.asList(members.split(","));
        } else {
            memberList = Collections.emptyList();
        }

        return createStudyAcls(userId, studyId, memberList, permissionList, template);
    }

    /**
     * Return all the ACLs defined in the study.
     *
     * @param userId user id asking for the ACLs.
     * @param studyId study id.
     * @return a list of studyAcls.
     * @throws CatalogException when the user asking to retrieve all the ACLs defined in the study does not have proper permissions.
     */
    QueryResult<StudyAcl> getAllStudyAcls(String userId, long studyId) throws CatalogException;

    /**
     * Return the ACL defined for the member.
     *
     * @param userId user asking for the ACL.
     * @param studyId study id.
     * @param member member whose permissions will be retrieved.
     * @return the studyAcl for the member.
     * @throws CatalogException if the user does not have proper permissions to see the member permissions.
     */
    QueryResult<StudyAcl> getStudyAcl(String userId, long studyId, String member) throws CatalogException;

    /**
     * Removes the ACLs defined for the member.
     *
     * @param userId user asking to remove the ACLs.
     * @param studyId study id.
     * @param member member whose permissions will be taken out.
     * @return the studyAcl prior to the deletion.
     * @throws CatalogException if the user asking to remove the ACLs does not have proper permissions or the member does not have any ACL
     * defined.
     */
    QueryResult<StudyAcl> removeStudyAcl(String userId, long studyId, String member) throws CatalogException;

    QueryResult<StudyAcl> updateStudyAcl(String userId, long studyId, String member, @Nullable String addPermissions,
                                         @Nullable String removePermissions, @Nullable String setPermissions) throws CatalogException;

    /**
     * Removes the members from the roleId specified.
     *
     * @param userId User id of the user ordering the action.
     * @param studyId Study id under which the members will be removed from the groupId.
     * @param members List of member ids (users and/or groups).
     * @throws CatalogException when the userId does not have the proper permissions to remove other users from roles, or the members or
     * roleId do not exist.
     */
    @Deprecated
    void removeStudyPermissions(String userId, long studyId, List<String> members) throws CatalogException;
    @Deprecated
    default void removeStudyPermissions(String userId, long studyId, String members)
            throws CatalogException {
        removeStudyPermissions(userId, studyId, Arrays.asList(members.split(",")));
    }

    //------------------------- Sample ACL -----------------------------

    QueryResult<SampleAcl> createSampleAcls(String userId, long sampleId, List<String> members, List<String> permissions)
            throws CatalogException;

    default QueryResult<SampleAcl> createSampleAcls(String userId, long sampleId, String members, String permissions)
            throws CatalogException {

        List<String> permissionList;
        if (permissions != null && !permissions.isEmpty()) {
            permissionList = Arrays.asList(permissions.split(","));
        } else {
            permissionList = Collections.emptyList();
        }

        List<String> memberList;
        if (members != null && !members.isEmpty()) {
            memberList = Arrays.asList(members.split(","));
        } else {
            memberList = Collections.emptyList();
        }

        return createSampleAcls(userId, sampleId, memberList, permissionList);
    }

    /**
     * Return all the ACLs defined for the sample.
     *
     * @param userId user id asking for the ACLs.
     * @param sampleId sample id.
     * @return a list of sampleAcls.
     * @throws CatalogException when the user asking to retrieve all the ACLs defined in the sample does not have proper permissions.
     */
    QueryResult<SampleAcl> getAllSampleAcls(String userId, long sampleId) throws CatalogException;

    /**
     * Return the ACL defined for the member.
     *
     * @param userId user asking for the ACL.
     * @param sampleId sample id.
     * @param member member whose permissions will be retrieved.
     * @return the SampleAcl for the member.
     * @throws CatalogException if the user does not have proper permissions to see the member permissions.
     */
    QueryResult<SampleAcl> getSampleAcl(String userId, long sampleId, String member) throws CatalogException;

    /**
     * Removes the ACLs defined for the member.
     *
     * @param userId user asking to remove the ACLs.
     * @param sampleId study id.
     * @param member member whose permissions will be taken out.
     * @return the SampleAcl prior to the deletion.
     * @throws CatalogException if the user asking to remove the ACLs does not have proper permissions or the member does not have any ACL
     * defined.
     */
    QueryResult<SampleAcl> removeSampleAcl(String userId, long sampleId, String member) throws CatalogException;

    QueryResult<SampleAcl> updateSampleAcl(String userId, long sampleId, String member, @Nullable String addPermissions,
                                         @Nullable String removePermissions, @Nullable String setPermissions) throws CatalogException;


    //------------------------- End of sample ACL ----------------------

    /**
     * Checks if the member belongs to one role or not.
     *
     * @param studyId study id.
     * @param member User or group id.
     * @return true if the member belongs to one role. False otherwise.
     * @throws CatalogException CatalogException.
     */
    boolean memberHasPermissionsInStudy(long studyId, String member) throws CatalogException;
}