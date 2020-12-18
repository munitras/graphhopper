/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.json.Clause;
import com.graphhopper.json.geo.JsonFeature;
import com.graphhopper.util.Parameters;

import java.util.*;

/**
 * This class is used in combination with CustomProfile.
 */
public class CustomModel {

    public static final String KEY = "custom_model";

    static double DEFAULT_D_I = 70;
    // optional:
    private Double maxSpeedFallback;
    private Double headingPenalty = Parameters.Routing.DEFAULT_HEADING_PENALTY;
    // default value derived from the cost for time e.g. 25€/hour and for distance 0.5€/km, for trucks this is usually larger
    private double distanceInfluence = DEFAULT_D_I;
    private List<Clause> speedFactorClauses = new ArrayList<>();
    private List<Clause> maxSpeedClauses = new ArrayList<>();
    private List<Clause> priorityClauses = new ArrayList<>();
    private Map<String, JsonFeature> areas = new HashMap<>();

    public CustomModel() {
    }

    public CustomModel(CustomModel toCopy) {
        this.maxSpeedFallback = toCopy.maxSpeedFallback;
        this.headingPenalty = toCopy.headingPenalty;
        this.distanceInfluence = toCopy.distanceInfluence;

        speedFactorClauses = deepCopy(toCopy.getSpeedFactor());
        maxSpeedClauses = deepCopy(toCopy.getMaxSpeed());
        priorityClauses = deepCopy(toCopy.getPriority());

        areas.putAll(toCopy.getAreas());
    }

    private <T> T deepCopy(T originalObject) {
        if (originalObject instanceof List) {
            List<Object> newList = new ArrayList<>(((List) originalObject).size());
            for (Object item : (List) originalObject) {
                newList.add(deepCopy(item));
            }
            return (T) newList;
        } else if (originalObject instanceof Map) {
            Map copy = originalObject instanceof LinkedHashMap ? new LinkedHashMap<>(((Map) originalObject).size()) :
                    new HashMap<>(((Map) originalObject).size());
            for (Object o : ((Map) originalObject).entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                copy.put(entry.getKey(), deepCopy(entry.getValue()));
            }
            return (T) copy;
        } else {
            return originalObject;
        }
    }

    public List<Clause> getSpeedFactor() {
        return speedFactorClauses;
    }

    public List<Clause> getMaxSpeed() {
        return maxSpeedClauses;
    }

    public CustomModel setMaxSpeedFallback(Double maxSpeedFallback) {
        this.maxSpeedFallback = maxSpeedFallback;
        return this;
    }

    public Double getMaxSpeedFallback() {
        return maxSpeedFallback;
    }

    public List<Clause> getPriority() {
        return priorityClauses;
    }

    public CustomModel setAreas(Map<String, JsonFeature> areas) {
        this.areas = areas;
        return this;
    }

    public Map<String, JsonFeature> getAreas() {
        return areas;
    }

    public CustomModel setDistanceInfluence(double distanceFactor) {
        this.distanceInfluence = distanceFactor;
        return this;
    }

    public double getDistanceInfluence() {
        return distanceInfluence;
    }

    public void setHeadingPenalty(double headingPenalty) {
        this.headingPenalty = headingPenalty;
    }

    public double getHeadingPenalty() {
        return headingPenalty;
    }

    @Override
    public String toString() {
        return createContentString();
    }

    private String createContentString() {
        // used to check against stored custom models, see #2026
        return "distanceInfluence=" + distanceInfluence + "|speedFactor=" + speedFactorClauses + "|maxSpeed=" + maxSpeedClauses +
                "|maxSpeedFallback=" + maxSpeedFallback + "|priorityMap=" + priorityClauses + "|areas=" + areas;
    }

    /**
     * A new CustomModel is created from the baseModel merged with the specified queryModel.
     */
    public static CustomModel merge(CustomModel baseModel, CustomModel queryModel) {
        // avoid changing the specified CustomModel via deep copy otherwise the server-side CustomModel would be modified (same problem if queryModel would be used as target)
        CustomModel mergedCM = new CustomModel(baseModel);
        if (queryModel.maxSpeedFallback != null) {
            if (mergedCM.maxSpeedFallback != null && mergedCM.maxSpeedFallback > queryModel.maxSpeedFallback)
                throw new IllegalArgumentException("CustomModel in query can only use max_speed_fallback bigger or equal to " + mergedCM.maxSpeedFallback);
            mergedCM.maxSpeedFallback = queryModel.maxSpeedFallback;
        }
        if (Math.abs(queryModel.distanceInfluence - CustomModel.DEFAULT_D_I) > 0.01) {
            if (mergedCM.distanceInfluence > queryModel.distanceInfluence)
                throw new IllegalArgumentException("CustomModel in query can only use distance_influence bigger or equal to " + mergedCM.distanceInfluence);
            mergedCM.distanceInfluence = queryModel.distanceInfluence;
        }

        // TODO NOW ensure all lists start with ifClause!

        check(queryModel.getPriority());
        check(queryModel.getSpeedFactor());

        mergedCM.maxSpeedClauses.addAll(queryModel.getMaxSpeed());
        mergedCM.speedFactorClauses.addAll(queryModel.getSpeedFactor());
        mergedCM.priorityClauses.addAll(queryModel.getPriority());

        for (Map.Entry<String, JsonFeature> entry : queryModel.getAreas().entrySet()) {
            if (mergedCM.areas.containsKey(entry.getKey()))
                throw new IllegalArgumentException("area " + entry.getKey() + " already exists");
            mergedCM.areas.put(entry.getKey(), entry.getValue());
        }

        return mergedCM;
    }

    private static void check(List<Clause> list) {
        for (Clause clause : list) {
            if (clause.getValue() > 1)
                throw new IllegalArgumentException("factor cannot be larger than 1 but was " + clause.getValue());
        }
    }
}