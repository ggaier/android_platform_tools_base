# A set of trace points to trace a build from the command line
# Add the agent flag via _JAVA_OPTIONS to get both the wrapper and gradle instrumented
# Trace-Agent: true

# Gradle wraper end-to-end events
Start: org.gradle.wrapper.GradleWrapperMain::main
Flush: org.gradle.launcher.Main::doAction

# Main configuration step
Trace: org.gradle.configuration.DefaultBuildConfigurer::configure
# Main execution step
Trace: org.gradle.execution.DefaultBuildExecuter::execute
Trace: org.gradle.execution.DefaultBuildConfigurationActionExecuter::configure

# Android plugin
Trace: com.android.build.gradle.BasePlugin
Annotation: org.gradle.api.tasks.TaskAction

# Apk step
Trace: com.android.build.gradle.tasks.PackageAndroidArtifact::doIncrementalTaskAction
Trace: com.android.build.gradle.tasks.PackageAndroidArtifact::doFullTaskAction
Trace: com.android.tools.build.apkzlib.zip.ZFile::update
Trace: com.android.tools.build.apkzlib.zfile.ApkZFileCreator
Trace: com.android.tools.build.apkzlib.zfile.ApkZFileCreatorFactory
Trace: com.android.build.gradle.internal.packaging.IncrementalPackagerBuilder

# Signing
Trace: com.android.tools.build.apkzlib.sign.SigningExtension::isCurrentSignatureAsRequested 
Trace: com.android.tools.build.apkzlib.sign.SigningExtension::onOutputZipReadyForUpdate
Trace: com.android.tools.build.apkzlib.sign.SigningExtension::onOutputZipEntriesWritten

# Flush the daemon
Flush: org.gradle.internal.buildevents.BuildResultLogger::buildFinished

# Task executors
# All: Trace: org.gradle.api.internal.tasks.execution.*
Trace: org.gradle.api.internal.tasks.execution.SkipUpToDateTaskExecuter::execute
Trace: org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter::execute

# Snapshot/checksumming:
# Trace: org.gradle.api.internal.changedetection.state.AbstractFileCollectionSnapshotter::snapshot
# Trace: org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter::snapshotDirectoryTree

# More details:
# Trace: org.gradle.api.internal.artifacts.configurations.DefaultConfiguration::getFiles
# Trace: org.gradle.api.internal.changedetection.state.CompileClasspathSnapshotBuilder
# Trace: org.gradle.api.internal.changedetection.state.RuntimeClasspathSnapshotBuilder
# Trace: org.gradle.api.internal.file.CalculatedTaskInputFileCollection::getFiles
# Trace: org.gradle.jvm.internal.DependencyResolvingClasspath
# Trace: org.gradle.jvm.internal.DependencyResolvingClasspath::getFiles
# Trace: org.gradle.api.internal.artifacts.ivyservice.DefaultConfigurationResolver
# Trace: org.gradle.api.internal.artifacts.configurations.DefaultConfiguration$ConfigurationFileCollection
# Trace: org.gradle.api.internal.artifacts.configurations.DefaultConfiguration#getFiles
# Trace: org.gradle.api.internal.file.AbstractFileTree::getFiles

# Even more details:
# Trace: org.gradle.api.internal.AbstractTask::execute
# Trace: org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter
# Trace: org.gradle.internal.execution.history.changes.*
# Trace: org.gradle.api.internal.project.taskfactory.*
# Trace: org.gradle.api.internal.project.taskfactory.*
# Trace: org.gradle.api.internal.artifacts.ivyservice.resolveengine.*
# Trace: org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.*
# Trace: org.gradle.api.internal.artifacts.transform.DefaultTransformedFileCache
# Trace: org.gradle.api.internal.artifacts.transform.TransformingAsyncArtifactListener
# Trace: org.gradle.api.internal.artifacts.transform.UserCodeBackedTransformer
# Trace: org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter$HashBackedSnapshot
# Trace: org.gradle.api.internal.file.AbstractFileCollection.*
# Trace: org.gradle.api.internal.changedetection.changes.DefaultTaskArtifactStateRepository$TaskArtifactStateImpl
# Trace: org.gradle.api.internal.changedetection.state.AbstractFileCollectionSnapshotter
# Trace: org.gradle.api.internal.changedetection.state.AbstractFileCollectionSnapshotter$FileCollectionVisitorImpl
# Trace: org.gradle.api.internal.changedetection.state.CacheBackedTaskHistoryRepository
# Trace: org.gradle.api.internal.changedetection.state.FileCollectionVisitingSnapshotBuilder
# Trace: org.gradle.api.internal.file.AbstractFileTree::getFiles
# Trace: org.gradle.api.internal.file.collections.FileCollectionAdapter::getFiles
# Trace: org.gradle.api.internal.file.CompositeFileCollection::getFiles
# Trace: org.gradle.api.internal.AbstractTask
# Trace: org.gradle.internal.buildevents.*
# Trace: org.gradle.internal.service.scopes.*
# Trace: org.gradle.execution.*
# Trace: org.gradle.configuration.*
# Trace: org.gradle.internal.buildevents.BuildResultLogger
