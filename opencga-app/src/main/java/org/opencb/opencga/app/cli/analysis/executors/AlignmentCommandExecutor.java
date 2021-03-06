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

package org.opencb.opencga.app.cli.analysis.executors;

import org.ga4gh.models.ReadAlignment;
import org.opencb.biodata.models.alignment.RegionCoverage;
import org.opencb.biodata.tools.alignment.stats.AlignmentGlobalStats;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.QueryResponse;
import org.opencb.opencga.app.cli.analysis.options.AlignmentCommandOptions;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.client.rest.OpenCGAClient;
import org.opencb.opencga.storage.core.alignment.AlignmentDBAdaptor;

import java.io.IOException;
import java.util.Map;

/**
 * Created on 09/05/16
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class AlignmentCommandExecutor extends AnalysisCommandExecutor {

    private final AlignmentCommandOptions alignmentCommandOptions;
//    private AlignmentStorageEngine alignmentStorageManager;

    public AlignmentCommandExecutor(AlignmentCommandOptions options) {
        super(options.analysisCommonOptions);
        alignmentCommandOptions = options;
    }

    @Override
    public void execute() throws Exception {
        logger.debug("Executing variant command line");

//        String subCommandString = alignmentCommandOptions.getParsedSubCommand();
        String subCommandString = getParsedSubCommand(alignmentCommandOptions.jCommander);
        configure();
        switch (subCommandString) {
            case "index":
                index();
                break;
            case "query":
                query();
                break;
            case "stats":
                stats();
                break;
            case "coverage":
                coverage();
                break;
            case "delete":
                delete();
                break;
            default:
                logger.error("Subcommand not valid");
                break;

        }
    }

    private void index() throws Exception {
        AlignmentCommandOptions.IndexAlignmentCommandOptions cliOptions = alignmentCommandOptions.indexAlignmentCommandOptions;

        ObjectMap params = new ObjectMap();
        if (!cliOptions.load && !cliOptions.transform) {  // if not present --transform nor --load,
            // do both
            params.put("extract", true);
            params.put("load", true);
            params.put("transform", true);
        } else {
            params.put("extract", cliOptions.transform);
            params.put("load", cliOptions.load);
            params.put("transform", cliOptions.transform);
        }

        String sessionId = cliOptions.commonOptions.sessionId;

        org.opencb.opencga.storage.core.manager.AlignmentStorageManager alignmentStorageManager =
                new org.opencb.opencga.storage.core.manager.AlignmentStorageManager(catalogManager, storageEngineFactory);
        alignmentStorageManager.index(cliOptions.study, cliOptions.fileId, params, sessionId);
    }


    private void query() throws InterruptedException, CatalogException, IOException {
        ObjectMap objectMap = new ObjectMap();
        objectMap.putIfNotNull("sid", alignmentCommandOptions.queryAlignmentCommandOptions.commonOptions.sessionId);
        objectMap.putIfNotNull("study", alignmentCommandOptions.queryAlignmentCommandOptions.study);
        objectMap.putIfNotNull(AlignmentDBAdaptor.QueryParams.REGION.key(), alignmentCommandOptions.queryAlignmentCommandOptions.region);
        objectMap.putIfNotNull(AlignmentDBAdaptor.QueryParams.MIN_MAPQ.key(),
                alignmentCommandOptions.queryAlignmentCommandOptions.minMappingQuality);
        objectMap.putIfNotNull(AlignmentDBAdaptor.QueryParams.CONTAINED.key(),
                alignmentCommandOptions.queryAlignmentCommandOptions.contained);
        objectMap.putIfNotNull(AlignmentDBAdaptor.QueryParams.MD_FIELD.key(),
                alignmentCommandOptions.queryAlignmentCommandOptions.mdField);
        objectMap.putIfNotNull(AlignmentDBAdaptor.QueryParams.BIN_QUALITIES.key(),
                alignmentCommandOptions.queryAlignmentCommandOptions.binQualities);
        objectMap.putIfNotNull("count", alignmentCommandOptions.queryAlignmentCommandOptions.count);
        objectMap.putIfNotNull(AlignmentDBAdaptor.QueryParams.LIMIT.key(), alignmentCommandOptions.queryAlignmentCommandOptions.limit);
        objectMap.putIfNotNull(AlignmentDBAdaptor.QueryParams.SKIP.key(), alignmentCommandOptions.queryAlignmentCommandOptions.skip);

        OpenCGAClient openCGAClient = new OpenCGAClient(clientConfiguration);
        QueryResponse<ReadAlignment> alignments = openCGAClient.getAlignmentClient()
                .query(alignmentCommandOptions.queryAlignmentCommandOptions.fileId, objectMap);

        for (ReadAlignment readAlignment : alignments.allResults()) {
            System.out.println(readAlignment);
        }
    }

    private void stats() throws CatalogException, IOException {
        ObjectMap objectMap = new ObjectMap();
        objectMap.putIfNotNull("sid", alignmentCommandOptions.statsAlignmentCommandOptions.commonOptions.sessionId);
        objectMap.putIfNotNull("study", alignmentCommandOptions.statsAlignmentCommandOptions.study);
        objectMap.putIfNotNull("region", alignmentCommandOptions.statsAlignmentCommandOptions.region);
        objectMap.putIfNotNull("minMapQ", alignmentCommandOptions.statsAlignmentCommandOptions.minMappingQuality);
        if (alignmentCommandOptions.statsAlignmentCommandOptions.contained) {
            objectMap.put("contained", alignmentCommandOptions.statsAlignmentCommandOptions.contained);
        }

        OpenCGAClient openCGAClient = new OpenCGAClient(clientConfiguration);
        QueryResponse<AlignmentGlobalStats> globalStats = openCGAClient.getAlignmentClient()
                .stats(alignmentCommandOptions.statsAlignmentCommandOptions.fileId, objectMap);

        for (AlignmentGlobalStats alignmentGlobalStats : globalStats.allResults()) {
            System.out.println(alignmentGlobalStats.toJSON());
        }
    }

    private void coverage() throws CatalogException, IOException {
        ObjectMap objectMap = new ObjectMap();
        objectMap.putIfNotNull("sid", alignmentCommandOptions.coverageAlignmentCommandOptions.commonOptions.sessionId);
        objectMap.putIfNotNull("study", alignmentCommandOptions.coverageAlignmentCommandOptions.study);
        objectMap.putIfNotNull("region", alignmentCommandOptions.coverageAlignmentCommandOptions.region);
        objectMap.putIfNotNull("minMapQ", alignmentCommandOptions.coverageAlignmentCommandOptions.minMappingQuality);
        if (alignmentCommandOptions.coverageAlignmentCommandOptions.contained) {
            objectMap.put("contained", alignmentCommandOptions.coverageAlignmentCommandOptions.contained);
        }

        OpenCGAClient openCGAClient = new OpenCGAClient(clientConfiguration);
        QueryResponse<RegionCoverage> globalStats = openCGAClient.getAlignmentClient()
                .coverage(alignmentCommandOptions.coverageAlignmentCommandOptions.fileId, objectMap);

        for (RegionCoverage regionCoverage : globalStats.allResults()) {
            System.out.println(regionCoverage.toString());
        }
    }

    private void delete() {
        throw new UnsupportedOperationException();
    }

    private void addParam(Map<String, String> map, String key, Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof String) {
            if (!((String) value).isEmpty()) {
                map.put(key, (String) value);
            }
        } else if (value instanceof Integer) {
            map.put(key, Integer.toString((int) value));
        } else if (value instanceof Boolean) {
            map.put(key, Boolean.toString((boolean) value));
        } else {
            throw new UnsupportedOperationException();
        }
    }

}
