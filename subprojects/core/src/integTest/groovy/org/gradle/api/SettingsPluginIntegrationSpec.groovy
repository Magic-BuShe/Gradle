/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository

class SettingsPluginIntegrationSpec extends AbstractIntegrationSpec {

    @org.junit.Rule
    MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @org.junit.Rule
    MavenHttpPluginRepository mavenHttpRepo = new MavenHttpPluginRepository(mavenRepo)

    def setup(){
        executer.usingSettingsFile(settingsFile)
        settingsFile << "rootProject.projectDir = file('..')\n"
    }

    def "can apply plugin class from settings.gradle"() {
        when:
        settingsFile << """
        apply plugin: SimpleSettingsPlugin

        class SimpleSettingsPlugin implements Plugin<Settings> {
            void apply(Settings mySettings) {
                mySettings.include("moduleA");
            }
        }
        """

        then:
        succeeds(':moduleA:dependencies')
    }

    def "can apply plugin class from buildSrc"() {
        setup:
        file("settings/buildSrc/src/main/java/test/SimpleSettingsPlugin.java").createFile().text = """
            package test;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class SimpleSettingsPlugin implements Plugin<Settings> {
                public void apply(Settings settings) {
                    settings.include(new String[]{"moduleA"});
                }
            }

            """
        file("settings/buildSrc/src/main/resources/META-INF/gradle-plugins/simple-plugin.properties").createFile().text = """
        implementation-class=test.SimpleSettingsPlugin
        """

        when:
        executer.expectDeprecationWarnings(1)
        settingsFile << "apply plugin: 'simple-plugin'"

        then:
        succeeds(':moduleA:dependencies')
        outputContains("Access to the buildSrc project and its dependencies in settings scripts has been deprecated.")
    }

    def "can apply script with relative path"() {
        setup:
        testDirectory.createFile("settings/somePath/settingsPlugin.gradle") << "apply from: 'path2/settings.gradle'";
        testDirectory.createFile("settings/somePath/path2/settings.gradle") << "include 'moduleA'";

        when:
        settingsFile << "apply from: 'somePath/settingsPlugin.gradle'"

        then:
        succeeds(':moduleA:dependencies')
    }

    def "can use plugins block"() {
        given:
        def pluginBuilder = new PluginBuilder(file("plugin"))
        def message = "hello from settings plugin"
        pluginBuilder.addSettingsPlugin("println '$message'")
        pluginBuilder.publishAs("g", "a", "1.0", pluginPortal, createExecuter()).allowAll()

        when:
        settingsFile.text = """
            plugins {
                id "test-settings-plugin" version "1.0"   
            }
            
            $settingsFile.text
        """

        then:
        succeeds("help")

        and:
        outputContains(message)
    }

    def "can use plugins block with plugin management block"() {
        given:
        def pluginBuilder = new PluginBuilder(file("plugin"))
        def message = "hello from settings plugin"
        pluginBuilder.addSettingsPlugin("println '$message'")
        pluginBuilder.publishAs("g", "a", "1.0", mavenHttpRepo, createExecuter()).allowAll()

        when:
        settingsFile.text = """
            pluginManagement {
                repositories {
                    maven { url "$mavenHttpRepo.uri" }
                }
            }   
            plugins {
                id "test-settings-plugin" version "1.0"   
            }
            
            $settingsFile.text
        """

        then:
        succeeds("help")

        and:
        outputContains(message)
    }

    protected TestFile getSettingsFile() {
        testDirectory.file('settings/settings.gradle')
    }
}
