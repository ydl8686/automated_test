import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.CancelException;

import java.io.*;
import java.util.*;


public class Test {
    public static ArrayList<String> vertex=new ArrayList<>();
    public static ArrayList<ArrayList<Integer>> adjacent=new ArrayList<>();
    public static CHACallGraph chaCG;
    public static String target;
    public static HashSet<String> changes=new HashSet<>();
    public static HashMap<String,HashSet<String>> methods=new HashMap<>();
    public HashSet<String> selectedTests = new HashSet<>();

    public static void main(String[] args) throws IOException, InvalidClassFileException, ClassHierarchyException, CancelException {
        //传进来的参数分为3部分
        //1不同级别的测试选择
        //2待测项目的target文件
        //3记录变更信息文件的文件路径
        String type=args[0];
        target=args[1];
        String changeInfo=args[2];
        SomeClass someClass=new SomeClass();
        AnalysisScope scope=someClass.loadClass();
        if (!target.endsWith(File.separator))
            target += File.separator;
        addClass(scope,new File(target+"test-classes"));
        System.out.println(scope);
        ClassHierarchy cha= ClassHierarchyFactory.makeWithRoot(scope);
        Iterable<Entrypoint> eps=new AllApplicationEntrypoints(scope,cha);
        chaCG=new CHACallGraph(cha);
        chaCG.init(eps);
        for (CGNode node : chaCG) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    String signature = method.getSignature();
                    // 放入测试类
                    if(!methods.containsKey(classInnerName)){
                        methods.put(classInnerName,new HashSet<>());
                    }
                    Collection<Annotation> annotations = method.getAnnotations();
                    for(Annotation annotation:annotations){
                        if(annotation.toString().contains("Lorg/junit/Test")){
                            methods.get(classInnerName).add(signature);
                            break;
                        }
                    }
                }
            }
        }
        addClass(scope,new File(target+"classes"));
        System.out.println(scope);
        cha=ClassHierarchyFactory.makeWithRoot(scope);
        eps=new AllApplicationEntrypoints(scope,cha);
        chaCG=new CHACallGraph(cha);
        chaCG.init(eps);
        getChange(changeInfo);
        Test t=new Test();
        if(type.equals("-c")){
            t.dotClass();
        }
        else{
            t.dotMethod();
        }
    }

    public static void addClass(AnalysisScope scope,File filePath) throws InvalidClassFileException {
        File[] list=filePath.listFiles();
        for (File file: list){
            if(file.isDirectory()){
                addClass(scope,file);
            }
            else{
                scope.addClassFileToScope(ClassLoaderReference.Application,file);
            }
        }
    }
    public static void getChange(String path){
        try{
            Scanner sc=new Scanner(new FileReader(path));
            while (sc.hasNextLine()){
                changes.add(sc.nextLine());
            }
        }
        catch (Exception e){

        }
    }
    public static String generateDotFile(String project,ArrayList<String> vertex,ArrayList<ArrayList<Integer>> adjacent){
        String result="digraph"+project+"{\n";
        for (int i=0;i<vertex.size();i++){
            String t=vertex.get(i);
            if(adjacent.get(i).isEmpty()){
                continue;
            }
            for (int j:adjacent.get(i)){
                String tem=vertex.get(j);
                result+="\t\""+t+"\" -> \""+tem+"\";\n";
            }
        }
        result+="}";
        return result;
    }

    public static void saveFile(String path,String dot){
        try{
            Writer w=new FileWriter(path);
            w.write(dot);
            w.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void dotClass(){
        //把图的顶点记录下来
        for (CGNode node : chaCG) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    if(!vertex.contains(classInnerName)){
                        vertex.add(classInnerName);
                        adjacent.add(new ArrayList<>());
                    }
                }
            }
        }
        //构建一张邻接表
        for (CGNode node : chaCG) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    int index=vertex.indexOf(classInnerName);
                    Iterator<CGNode> t=chaCG.getPredNodes(node);
                    while(t.hasNext()){
                        CGNode anotherNode=t.next();
                        if(anotherNode.getMethod() instanceof ShrikeBTMethod){
                            ShrikeBTMethod tMethod=(ShrikeBTMethod)anotherNode.getMethod();
                            if ("Application".equals(tMethod.getDeclaringClass().getClassLoader().toString())) {
                                String callerInnerName = tMethod.getDeclaringClass().getName().toString();
                                Integer callerIndex = vertex.indexOf(callerInnerName);
                                if (!adjacent.get(index).contains(callerIndex))
                                    adjacent.get(index).add(callerIndex);
                            }
                        }
                    }
                }
            }
        }
        String[] temp=target.split("/|\\\\");
        String projectName=temp[temp.length-2].split("-")[1];
        String result=generateDotFile(projectName,vertex,adjacent);
        saveFile("class-"+projectName+".dot",result);
        HashSet<String> changedClasses = new HashSet<>();
        for (String change : changes) {
            changedClasses.add(change.split(" ")[0]);
        }
        for (String ct : changedClasses) {
            traverse(new HashSet<>(), vertex.indexOf(ct));
        }
        String content = "";
        for (String test : selectedTests) {
            content += test + '\n';
        }
        saveFile("selection-class.txt", content);
    }

    public void dotMethod(){
        for (CGNode node : chaCG) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                // 使用Primordial类加载器加载的类都属于Java原生类，一般不关心
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    String signature = method.getSignature();
                    String v = classInnerName + ' ' + signature;
                    if (!vertex.contains(v)) {
                        vertex.add(v);
                        adjacent.add(new ArrayList<>());
                    }
                }
            }
        }
        for (CGNode node : chaCG) {
            if (node.getMethod() instanceof ShrikeBTMethod) {
                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    String signature = method.getSignature();
                    String callee = classInnerName + ' ' + signature;
                    int calleeIndex = vertex.indexOf(callee);
                    // 获取本方法的调用者
                    Iterator<CGNode> callerNodesItr = chaCG.getPredNodes(node);
                    while (callerNodesItr.hasNext()) {
                        CGNode callerNode = callerNodesItr.next();
                        if (callerNode.getMethod() instanceof ShrikeBTMethod) {
                            ShrikeBTMethod callerMethod = (ShrikeBTMethod) callerNode.getMethod();
                            if ("Application".equals(callerMethod.getDeclaringClass().getClassLoader().toString())) {
                                String callerInnerName = callerMethod.getDeclaringClass().getName().toString();
                                String callerSigniture = callerMethod.getSignature();
                                String caller = callerInnerName + ' ' + callerSigniture;
                                Integer callerIndex = vertex.indexOf(caller);
                                if (!adjacent.get(calleeIndex).contains(callerIndex))
                                    adjacent.get(calleeIndex).add(callerIndex);
                            }
                        }
                    }
                }
            }
        }
        String[] temp=target.split("/|\\\\");
        String projectName=temp[temp.length-2].split("-")[1];
        String result=generateDotFile(projectName,vertex,adjacent);
        saveFile("method-"+projectName+".dot",result);
        for (String change : changes) {
            traverse(new HashSet<>(),vertex.indexOf(change));
        }
        String content = "";
        for (String test : selectedTests) {
            content += test + '\n';
        }
        saveFile("selection-method.txt", content);
    }
    //用来遍历依赖
    public void traverse(HashSet<Integer> hash, int index){
        if (hash.contains(index))
            return;
        hash.add(index);
        for (int callerIndex : adjacent.get(index)) {
            String caller = vertex.get(callerIndex);
            if (methods.containsKey(caller)){
                HashSet<String> method = methods.get(caller);
                for(String m:method){
                    selectedTests.add(caller+' '+m);
                }
            }
            traverse(hash, callerIndex);
        }
    }
}
