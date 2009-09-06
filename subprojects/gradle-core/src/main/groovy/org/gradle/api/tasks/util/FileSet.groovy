/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.util

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.AbstractFileTree
import org.gradle.api.internal.file.BreadthFirstDirectoryWalker
import org.gradle.api.internal.file.CopyActionImpl
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.WorkResult
import org.gradle.util.ConfigureUtil

/**
 * @author Hans Dockter
 */
class FileSet extends AbstractFileTree implements ConfigurableFileTree {
    PatternSet patternSet = new PatternSet()
    private File dir
    FileResolver resolver

    FileSet(Object baseDir, FileResolver resolver) {
        this([baseDir: baseDir], resolver)
    }

    FileSet(Map args, FileResolver resolver) {
        this.resolver = resolver
        args.each {String key, value ->
            this.setProperty(key, value)
        }
    }

    public FileSet setBaseDir(Object baseDir) {
        from(baseDir)
    }

    public FileSet from(Object baseDir) {
        this.dir = resolver.resolve(baseDir)
        this
    }

    public String getDisplayName() {
        "file set '$dir'"
    }

    FileTree matching(Closure filterConfigClosure) {
        PatternSet patternSet = this.patternSet.intersect()
        ConfigureUtil.configure(filterConfigClosure, patternSet)
        FileSet filtered = new FileSet(baseDir, resolver)
        filtered.patternSet = patternSet
        filtered
    }

    FileTree matching(PatternFilterable patterns) {
        PatternSet patternSet = this.patternSet.intersect()
        patternSet.copyFrom(patterns)
        FileSet filtered = new FileSet(baseDir, resolver)
        filtered.patternSet = patternSet
        filtered
    }

    FileTree visit(FileVisitor visitor) {
        BreadthFirstDirectoryWalker walker = new BreadthFirstDirectoryWalker(visitor)
        walker.match(patternSet).start(baseDir)
        this
    }

    public WorkResult copy(Closure closure) {
        CopyActionImpl action = new CopyActionImpl(resolver)
        action.from(baseDir)
        action.include(getIncludes())
        action.exclude(getExcludes())
        ConfigureUtil.configure(closure, action)
        action.execute()
        return action
    }

    public File getBaseDir() {
        if (!dir) { throw new InvalidUserDataException('A basedir must be specified in the task or via a method argument!') }
        dir
    }

    public Set<String> getIncludes() {
        patternSet.includes
    }

    public PatternFilterable setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes)
        this
    }

    public Set<String> getExcludes() {
        patternSet.excludes
    }

    public PatternFilterable setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes)
        this
    }

    public PatternFilterable include(String ... includes) {
        patternSet.include(includes)
        this
    }

    public PatternFilterable include(Iterable<String> includes) {
        patternSet.include(includes)
        this
    }

    public PatternFilterable exclude(String ... excludes) {
        patternSet.exclude(excludes)
        this
    }

    public PatternFilterable exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes)
        this
    }

    def addToAntBuilder(node, String childNodeName = null) {
        File dir = getBaseDir()
        if (!dir.exists()) {
            return
        }
        node."${childNodeName ?: 'fileset'}"(dir: dir.absolutePath) {
            patternSet.addToAntBuilder(node)
        }
    }
}
