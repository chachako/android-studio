# A set of trace points to trace r8 desugaring

# Main configuration step
Start: com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler::compile

# Interest points
Trace: com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler::*
Trace: com.android.tools.idea.run.deployment.liveedit.LiveEditDesugarerKt::*
Trace: com.android.tools.r8.D8::*
Trace: com.android.tools.r8.D8Command::*
Trace: com.android.tools.r8.D8Command$Builder::*
Trace: com.android.tools.r8.BaseCompilerCommand::*
Trace: com.android.tools.r8.BaseCommand::*
Trace: com.android.tools.r8.ClassFileResourceProvider::*
Trace: com.android.tools.r8.utils.AndroidApp::*
Trace: com.android.tools.r8.utils.InternalArchiveClassFileProvider::*
Trace: com.android.tools.r8.graph.analysis.ClassInitializerAssertionEnablingAnalysis::*
Trace: com.android.tools.r8.graph.DexApplication::*

Flush: com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler::compile
