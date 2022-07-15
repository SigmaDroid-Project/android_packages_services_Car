/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "../lua_engine.h"

#include <gtest/gtest.h>

#include <iostream>
#include <sstream>
#include <string>
#include <vector>

namespace lua_interpreter {
namespace tests {

// The fixture for testing class LuaEngine
class LuaEngineTest : public testing::Test {
 protected:
  lua_interpreter::LuaEngine lua_engine_;

  std::string ConvertVectorToString(std::vector<std::string> vector) {
    std::stringstream output;
    for (std::string s : vector) {
      output << s;
    }
    return output.str();
  }

  std::string ConvertArrayToString(char** array, int size) {
    std::stringstream output;
    for (int i = 0; i < size; i++) {
      output << array[i];
    }
    return output.str();
  }
};

TEST_F(LuaEngineTest, ExecuteScriptEmptyScriptSendsNoOutput) {
  std::vector<std::string> output = lua_engine_.ExecuteScript("");
  EXPECT_EQ(0, output.size());
}

TEST_F(LuaEngineTest, ExecuteScriptNoExplicitReturnSendsNoOutput) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("function two() return 2 end");
  EXPECT_EQ(0, output.size());
}

TEST_F(LuaEngineTest, ExecuteScriptSyntaxError) {
  std::vector<std::string> output = lua_engine_.ExecuteScript("f");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find("Error encountered while loading the script.") !=
              std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptRuntimeError) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function add(a, b) return a + b end return add(10)");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find("Error encountered while running the script.") !=
              std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptReturnsOutput) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function add(a, b) return a + b end return add(10, 5)");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find("15") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptLogCallback) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("log('Logging here')");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find("LUA: Logging here") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnSuccessMoreArguments) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_success({}, {})");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find("on_success can push only a single parameter from "
                          "Lua - a Lua table") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnSuccessNonTable) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_success('Success!')");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find("on_success can push only a single parameter from "
                          "Lua - a Lua table") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnSuccessWithTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "tbl = {}; tbl['sessionId'] = 1; on_success(tbl)");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find('{"sessionId":1}') != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnScriptFinishedMoreArguments) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_script_finished({}, {})");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find("on_script_finished can push only a single parameter "
                          "from Lua - a Lua "
                          "table") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnScriptFinishedNonTable) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_script_finished('Script finished')");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find("on_script_finished can push only a single parameter "
                          "from Lua - a Lua "
                          "table") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnScriptFinishedWithTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "tbl = {}; tbl['sessionId'] = 1; on_script_finished(tbl)");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find('{"sessionId":1}') != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnErrorMoreArguments) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_error('ERROR ONE', 'ERROR TWO')");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(
      actual.find(
          "on_error can push only a single string parameter from Lua") !=
      std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnErrorNonString) {
  std::vector<std::string> output = lua_engine_.ExecuteScript("on_error({})");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(
      actual.find(
          "on_error can push only a single string parameter from Lua") !=
      std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnErrorWithSingleString) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_error('ERROR: 2')");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find('ERROR: 2') != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportMoreArguments) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_metrics_report({}, {}, {})");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(
      actual.find(
          "on_metrics_report should push 1 to 2 parameters of Lua table type. "
          "The first table is a metrics report and the second is an optional "
          "state to save") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportNonTable) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_metrics_report('Incoming metrics report')");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(
      actual.find(
          "on_metrics_report should push 1 to 2 parameters of Lua table type. "
          "The first table is a metrics report and the second is an optional "
          "state to save") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportNonTableWithTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "on_metrics_report('Incoming metrics report', {})");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(
      actual.find(
          "on_metrics_report should push 1 to 2 parameters of Lua table type. "
          "The first table is a metrics report and the second is an optional "
          "state to save") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportTableWithNonTable) {
  std::vector<std::string> output =
      lua_engine_.ExecuteScript("on_metrics_report({}, 'Saved state here')");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(
      actual.find(
          "on_metrics_report should push 1 to 2 parameters of Lua table "
          "type. "
          "The first table is a metrics report and the second is an optional "
          "state to save") != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportSingleTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "tbl = {}; tbl['sessionId'] = 1; on_metrics_report(tbl)");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find('{"sessionId":1}') != std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportMultipleTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "tbl = {}; tbl['sessionId'] = 1; on_metrics_report(tbl, tbl)");
  std::string actual = ConvertVectorToString(output);
  EXPECT_TRUE(actual.find('{"sessionId":1}\n{"sessionId":1}') !=
              std::string::npos);
}

TEST_F(LuaEngineTest, StringVectorToArrayEmpty) {
  std::vector<std::string> vector = {};
  char** array = LuaEngine::StringVectorToCharArray(vector);
  EXPECT_EQ(nullptr, array);
}

TEST_F(LuaEngineTest, StringVectorToArrayNonEmpty) {
  std::vector<std::string> vector = {"1", "2", "3", "4"};
  char** array = LuaEngine::StringVectorToCharArray(vector);
  EXPECT_EQ("1234", ConvertArrayToString(array, 4));
}
}  // namespace tests
}  // namespace lua_interpreter

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}