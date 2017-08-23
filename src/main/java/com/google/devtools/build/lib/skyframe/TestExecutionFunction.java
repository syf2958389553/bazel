// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.skyframe;

import com.google.auto.value.AutoValue;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.analysis.ConfiguredTarget;
import com.google.devtools.build.lib.analysis.LabelAndConfiguration;
import com.google.devtools.build.lib.analysis.test.TestProvider;
import com.google.devtools.build.lib.cmdline.Label;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionName;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;
import javax.annotation.Nullable;

/** {@link SkyFunction} to ensure that a test has executed. */
class TestExecutionFunction implements SkyFunction {
  private static final SkyValue TEST_EXECUTION_MARKER = new SkyValue() {};

  static SkyKey key(LabelAndConfiguration lac, boolean exclusiveTesting) {
    return TestExecutionKey.create(lac, exclusiveTesting);
  }

  @AutoValue
  abstract static class TestExecutionKey implements SkyKey {
    abstract LabelAndConfiguration getLabelAndConfiguration();

    abstract boolean exclusiveTesting();

    static TestExecutionKey create(LabelAndConfiguration lac, boolean exclusiveTesting) {
      return new AutoValue_TestExecutionFunction_TestExecutionKey(lac, exclusiveTesting);
    }

    @Override
    public SkyFunctionName functionName() {
      return SkyFunctions.TEST_EXECUTION;
    }
  }

  @Nullable
  @Override
  public SkyValue compute(SkyKey skyKey, Environment env) throws InterruptedException {
    TestExecutionKey key = (TestExecutionKey) skyKey.argument();
    LabelAndConfiguration lac = key.getLabelAndConfiguration();
    ConfiguredTargetValue ctValue =
        (ConfiguredTargetValue)
            env.getValue(ConfiguredTargetValue.key(lac.getLabel(), lac.getConfiguration()));
    if (ctValue == null) {
      return null;
    }

    ConfiguredTarget ct = ctValue.getConfiguredTarget();
    if (key.exclusiveTesting()) {
      // Request test artifacts iteratively if testing exclusively.
      for (Artifact testArtifact : TestProvider.getTestStatusArtifacts(ct)) {
        if (env.getValue(ArtifactSkyKey.key(testArtifact, /*isMandatory=*/ true)) == null) {
          return null;
        }
      }
    } else {
      env.getValues(ArtifactSkyKey.mandatoryKeys(TestProvider.getTestStatusArtifacts(ct)));
      if (env.valuesMissing()) {
        return null;
      }
    }
    return TEST_EXECUTION_MARKER;
  }

  @Override
  public String extractTag(SkyKey skyKey) {
    return Label.print(
        ((TestExecutionKey) skyKey.argument()).getLabelAndConfiguration().getLabel());
  }
}
