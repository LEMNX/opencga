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

package org.opencb.opencga.catalog.db.mongodb.converters;

import org.bson.Document;
import org.opencb.commons.datastore.mongodb.GenericDocumentComplexConverter;
import org.opencb.opencga.core.models.Individual;
import org.opencb.opencga.core.models.Sample;

import java.util.List;

/**
 * Created by pfurio on 19/01/16.
 */
public class SampleConverter extends GenericDocumentComplexConverter<Sample> {

    public SampleConverter() {
        super(Sample.class);
    }

    @Override
    public Sample convertToDataModelType(Document object) {
        if (object.get("_individual") != null) {
            if (object.get("_individual") instanceof List) {
                if (((List) object.get("_individual")).size() > 0) {
                    object.put("_individual", ((List) object.get("_individual")).get(0));
                } else {
                    object.put("_individual", new Document("id", -1));
                }
            }
        }

        Document individual = (Document) object.get("_individual");
        if (individual != null) {
            individual.remove("_id");
            individual.remove("_studyId");
            individual.remove("_acl");
            individual.remove("annotationSets");

            // We add individual to attributes
            Document attributes = (Document) object.get("attributes");
            if (attributes == null) {
                attributes = new Document();
                object.put("attributes", attributes);
            }
            attributes.put("individual", individual);

            // Temporary until individual is completely removed from the data models.
            object.put("individual", individual);
        }
        return super.convertToDataModelType(object);
    }

    @Override
    public Document convertToStorageType(Sample object) {
        Document document = super.convertToStorageType(object);
        document.put("id", document.getInteger("id").longValue());
        document.remove("individual");
//        long individualId = object.getIndividual() != null
//                ? (object.getIndividual().getId() == 0 ? -1L : object.getIndividual().getId()) : -1L;
//        document.put("individual", new Document("id", individualId));
        return document;
    }

    public Document convertIndividual(Individual individual) {
        Document document = new Document();
        if (individual == null) {
            document.put("id", -1L);
        } else {
            document.put("id", individual.getId() > 0 ? individual.getId() : -1L);
        }
        return document;
    }
}
