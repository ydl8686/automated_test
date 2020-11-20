import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.File;
import java.io.IOException;

public class SomeClass {
    public AnalysisScope loadClass() throws IOException, InvalidClassFileException {
        ClassLoader classLoader=SomeClass.class.getClassLoader();
        AnalysisScope scope=AnalysisScopeReader.readJavaScope("scope.txt",new File("exclusion.txt"),classLoader);
//        scope.addClassFileToScope(ClassLoaderReference.Application,file);
        return scope;
    }
}
