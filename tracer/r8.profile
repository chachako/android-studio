# A set of trace points to trace r8 desugaring

# Interest points
Trace: com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler::*
Trace: com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugar::*
Trace: com.android.tools.idea.run.deployment.liveedit.desugaring.R8MemoryProgramResourceProvider::*
Trace: com.android.tools.idea.run.deployment.liveedit.desugaring.R8MemoryClassFileConsumer::*
Trace: com.android.tools.idea.run.deployment.liveedit.desugaring.R8DiagnosticHandler::*
Trace: com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugar.*::*
Trace: com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugarKt::*
Trace: com.android.tools.r8.D8::*
Trace: com.android.tools.r8.*::*
Trace: com.android.tools.r8.dump.*::*
Trace: com.android.tools.r8.D8Command::*
Trace: com.android.tools.r8.D8Command*::*
Trace: com.android.tools.r8.BaseCompilerCommand::*
Trace: com.android.tools.r8.BaseCommand::*
Trace: com.android.tools.r8.ClassFileResourceProvider::*
Trace: com.android.tools.r8.utils.AndroidApp::*
Trace: com.android.tools.r8.utils.InternalArchiveClassFileProvider::*
Trace: com.android.tools.r8.graph.analysis.ClassInitializerAssertionEnablingAnalysis::*
Trace: com.android.tools.r8.graph.DexApplication::*
Trace: com.android.zipflinger.ZipRepo::*
Trace: com.android.zipflinger.ZipMap::from

Flush: com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler::compile
