package core.monitoring;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import declare.DeclareParserException;
import core.exceptions.GenerationException;
import core.alloy.codegen.AlloyCodeGenerator;
import core.alloy.codegen.NameEncoder;
import core.alloy.integration.AlloyComponent;
import declare.lang.Constraint;
import declare.lang.DataConstraint;
import declare.DeclareModel;

import org.deckfour.xes.extension.std.XExtendedEvent;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XTraceImpl;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConstraintChecker {
    int i = 0;
    List<Constraint> constraints;
    List<DataConstraint> dataConstraints;
    DeclareModel model;
    NameEncoder encodings;
    XTrace trace;
    int minTraceLen;
    int maxTraceLen;
    String alsFilename = "monitoring_temp.als";
    boolean dummyRemoval;

    int bitwidth = 5;// Should maybe add this to the constructor also the filename or something, dno, lets think about it later.
    String [][] declaredMatrix;
    String [] constraintNames;
    String [] dataConstraintsName;
    DataConstraint toBeProcessedDataConstraint;
    ArrayList<ArrayList<DataConstraint>> conflictedConstraints = new ArrayList<>();
    DataConstraint[] allConstraintArr;
    ArrayList<Constraint> permViolatedCon = new ArrayList<>();
    ArrayList<DataConstraint> permViolatedDCon = new ArrayList<>();

    // Very cool hack indeed.
    public void setAllConstraintArr() {
        allConstraintArr = new  DataConstraint[constraints.size() + dataConstraints.size()];
        int i = 0;
        for (Constraint c : constraints){
            DataConstraint dc = new DataConstraint(c.getName(), c.getArgs(),null, c.getStatement());
            allConstraintArr[i] = dc;
            i++;
        }
        for (DataConstraint dc : dataConstraints){
            allConstraintArr[i] = dc;
            i++;
        }
    }

    public void setDummyRemoval(boolean dummyRemoval) {
        this.dummyRemoval = dummyRemoval;
    }

    public ConstraintChecker() {
        model = new DeclareModel();
        trace = new XTraceImpl(new XAttributeMapImpl());
    }

    public void setFinal() {
        for (Constraint c : constraints) {
            if (c.getState() == Constraint.State.POSSIBLY_SATISFIED)
                c.setState(Constraint.State.PERMANENTLY_SATISFIED);
            else if (c.getState() == Constraint.State.POSSIBLY_VIOLATED)
                c.setState(Constraint.State.PERMANENTLY_VIOLATED);
        }
        for (DataConstraint dc: dataConstraints){
            if (dc.getState() == Constraint.State.POSSIBLY_SATISFIED)
                dc.setState(Constraint.State.PERMANENTLY_SATISFIED);
            else if (dc.getState() == Constraint.State.POSSIBLY_VIOLATED)
                dc.setState(Constraint.State.PERMANENTLY_VIOLATED);
        }
    }

    public void run() throws DeclareParserException, Err, GenerationException {
        //constraints = model.getConstraints();
        if (!constraints.isEmpty() || constraints != null) {
            for (Constraint c : constraints) {
                String s = c.getName();
                checkerChooser(s, c);
                //NotRespondedExistence üle
                //NotRespone - OK, NotChainResponse - OK, Precedence - , NotChainPrecedence -OK, NotPrecedence 11 vist peab olema.
            }
        }
        
        if (!dataConstraints.isEmpty() || dataConstraints != null) {
            for (DataConstraint dc : dataConstraints) {
                String s = dc.getName();
                toBeProcessedDataConstraint = dc;
                checkerChooser(s, dc);
            }
        }
    }
    
    private void checkerChooser(String s, Constraint c) throws DeclareParserException, GenerationException, Err {
    	setInitialSize();
    	
    	if (s.equalsIgnoreCase("Response"))
            responseChecker(c);
        else if (s.equalsIgnoreCase("Existence") && (!c.isBinary()))
            existenceChecker(c);
        else if (s.equalsIgnoreCase("Init"))
            initChecker(c);
        else if (s.equalsIgnoreCase("Choice"))
            choiceChecker(c);
        else if (s.equalsIgnoreCase("Existence") && c.isBinary()) // Here goes choice
            existenceChecker(c, Integer.parseInt(c.taskB()));
        else if (s.equalsIgnoreCase("RespondedExistence"))
            responseChecker(c);
        else if (s.equalsIgnoreCase("ExclusiveChoice"))
            exclusiveChoiceChecker(c);
        else if (s.equalsIgnoreCase("Exactly"))
            exactlyChecker(c, Integer.parseInt(c.taskB()));
        else if (s.equalsIgnoreCase("AlternateResponse"))
            alternateResponseChecker(c);
        else if (s.equalsIgnoreCase("ChainResponse"))
            chainResponse(c);
        else if (s.equalsIgnoreCase("Absence"))
            absenceChecker(c);
        else if (s.equalsIgnoreCase("NotRespondedExistence") ||c.getName().equalsIgnoreCase("NotResponse")
                || c.getName().equalsIgnoreCase("NotChainResponse")|| c.getName().equalsIgnoreCase("NotChainPrecedence") || c.getName().equalsIgnoreCase("NotPrecedence") ||
                c.getName().equalsIgnoreCase("Precedence") || c.getName().equalsIgnoreCase("AlternatePrecedence") || c.getName().equalsIgnoreCase("ChainPrecedence"))
            notRespondedExistenceChecker(c);

    }

    public DeclareModel getModel() {
        return model;
    }

    public void setModel(DeclareModel model) {
        this.model = model;
    }

    public XTrace getTrace() {
        return trace;
    }

    public void setTrace(XTrace trace) {
        this.trace = trace;
    }
    
    public NameEncoder getEncodings() {
		return encodings;
	}

	public void setEncodings(NameEncoder encodings) {
		this.encodings = encodings;
	}

	// We set the initial Trace size:
    public void setInitialSize() {
        minTraceLen = trace.size();
        maxTraceLen = trace.size();
    }

    public void absenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (!checkConstraint(c)) { // If false then violated because that it is absence and something is present then this is bad.
            c.setState(Constraint.State.PERMANENTLY_VIOLATED);// should be this
            permViolatedCon.add(c);
        
        } else { // Possibly satisfied because we don't know whether there will be possibly this thing occurring in the future.
            c.setState(Constraint.State.POSSIBLY_SATISFIED);
        }
        
        //traceAndStatePrintOut(c);
    }
    
    public void choiceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c))
            c.setState(Constraint.State.PERMANENTLY_SATISFIED);
        else
            c.setState(Constraint.State.POSSIBLY_VIOLATED);
        
        //traceAndStatePrintOut(c);
        
    }
    public void exclusiveChoiceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) {
            c.setState(Constraint.State.POSSIBLY_SATISFIED);
        
    	} else {
            maxTraceLen++;
            if (checkConstraint(c)) {
                c.setState(Constraint.State.POSSIBLY_VIOLATED);
            
            } else {
                c.setState(Constraint.State.PERMANENTLY_VIOLATED);
                if (isDConstraint(c))
                    permViolatedDCon.add(toBeProcessedDataConstraint);
                else
                    permViolatedCon.add(c);
            }
        }
    	
    	//traceAndStatePrintOut(c);
    }

    public void respondedExistenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) {
            c.setState(Constraint.State.PERMANENTLY_SATISFIED);
        
    	} else {
            maxTraceLen++;
            if (checkConstraint(c))
                c.setState(Constraint.State.POSSIBLY_VIOLATED);
            else //TODO Review this checker
                c.setState(Constraint.State.POSSIBLY_VIOLATED);
        }
    	
    	//traceAndStatePrintOut(c);
    }

    public void responseChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) {
            c.setState(Constraint.State.POSSIBLY_SATISFIED);
        
        } else {
            int numberOfActivations = 0;
            String activation = c.taskA();
            c.setState(Constraint.State.POSSIBLY_VIOLATED);
            //traceAndStatePrintOut(c);
            
            for (XEvent ev : trace)
                if (ev.getAttributes().get("concept:name").toString().equals(activation))
                    numberOfActivations++;

            maxTraceLen = maxTraceLen + numberOfActivations;
            //System.out.println("Checking same constraint with a larger upper bound now: ");

            if (!checkConstraint(c))
                c.setState(Constraint.State.POSSIBLY_VIOLATED); // Because it cannot find a solution even with a larger upperbound.
            else
                c.setState(Constraint.State.POSSIBLY_VIOLATED); // Although we don't really need these lines do we
        }
        
        //traceAndStatePrintOut(c);
    }

    public void exactlyChecker(Constraint c, int n) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) {
            c.setState(Constraint.State.POSSIBLY_SATISFIED);
            
        } else { // if value wrong
            maxTraceLen = maxTraceLen + n;
            //System.out.println("Checking same constraint with prefix size + N: ");

            if (checkConstraint(c)) {
                c.setState(Constraint.State.POSSIBLY_VIOLATED); // right now violated but a possibility get it right in the future. A has occured less then N times
            
            } else {
                c.setState(Constraint.State.PERMANENTLY_VIOLATED); // Activity A has occurred more than N times which means that the trace is broken beyond repair.
                if (isDConstraint(c))
                    permViolatedDCon.add(toBeProcessedDataConstraint);
                else
                    permViolatedCon.add(c);
            }
        }
        
        //traceAndStatePrintOut(c);
    }

    public void existenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) {
            c.setState(Constraint.State.PERMANENTLY_SATISFIED); // Because when we get the value true here then it means it exists and it will be always satisfied from now on.
        
        } else {
            c.setState(Constraint.State.POSSIBLY_VIOLATED);
            //traceAndStatePrintOut(c);
            
            maxTraceLen++; // increase by one and check the value again.
            if (checkConstraint(c)) // if this is true then
                c.setState(Constraint.State.POSSIBLY_VIOLATED); // Is this right tho?
            else
                c.setState(Constraint.State.POSSIBLY_VIOLATED);
        }
    	
    	//traceAndStatePrintOut(c);
    }
    
    public void existenceChecker(Constraint c, int n) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) { // If this return true, than it means that this constraint is possibly satisfied, pos because could go wrong in the future.
            c.setState(Constraint.State.PERMANENTLY_SATISFIED);
        
        } else { // if value is false then err we check for maxTracesize + len.
            maxTraceLen = maxTraceLen + n; // yeap this is correct.
            c.setState(Constraint.State.POSSIBLY_VIOLATED);
            //traceAndStatePrintOut(c);
            //System.out.println("Checking same constraint with maxTraceLen + N: ");

            if (!checkConstraint(c)) { // if we don't find a solution then it is permanently violated if it is okay, then it will still be possibly violated.
                c.setState(Constraint.State.PERMANENTLY_VIOLATED);
                if (isDConstraint(c))
                    permViolatedDCon.add(toBeProcessedDataConstraint);
                else
                    permViolatedCon.add(c);
            }
        }
        
        //traceAndStatePrintOut(c);
    }
    
    private void initChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) { // if true, then all fine and the event will always be the first event
            c.setState(Constraint.State.PERMANENTLY_SATISFIED);
        
        } else {
            c.setState(Constraint.State.PERMANENTLY_VIOLATED);
            if (isDConstraint(c))
                permViolatedDCon.add(toBeProcessedDataConstraint);
            else
                permViolatedCon.add(c);
        }
        
        //traceAndStatePrintOut(c);
    }

    private void alternateResponseChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)){
            c.setState(Constraint.State.POSSIBLY_SATISFIED); // might get rekted
            //traceAndStatePrintOut(c);
            
        } else {
            //System.out.println("Checking same constraint for prefix size + 1: ");
            maxTraceLen = maxTraceLen+1;
            c.setState(Constraint.State.POSSIBLY_VIOLATED); // Def possibly violated.
            //traceAndStatePrintOut(c);
            
            if (!checkConstraint(c)) {
                c.setState(Constraint.State.PERMANENTLY_VIOLATED); // Well yeah, it is permanently violated.
                if (isDConstraint(c))
                    permViolatedDCon.add(toBeProcessedDataConstraint);
                else
                    permViolatedCon.add(c);
                
                //traceAndStatePrintOut(c);
            }
        }
    }
    
    private void chainResponse(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) {
            c.setState(Constraint.State.POSSIBLY_SATISFIED); // might get rekted
            
        } else {
            maxTraceLen++;
            //System.out.println("Checking same constraint for prefix size + 1");

            if (!checkConstraint(c)) {
                c.setState(Constraint.State.PERMANENTLY_VIOLATED); // if does not find the right solution with prefix + 1
                if (isDConstraint(c))
                    permViolatedDCon.add(toBeProcessedDataConstraint);
                else
                    permViolatedCon.add(c);
            
            } else {
                c.setState(Constraint.State.POSSIBLY_VIOLATED);
            }
        }
        
        //traceAndStatePrintOut(c);
    }
    
    private void precedenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (!checkConstraint(c))
            c.setState(Constraint.State.POSSIBLY_VIOLATED);
        else
            c.setState(Constraint.State.POSSIBLY_SATISFIED);
        
        //traceAndStatePrintOut(c);
    }
    
    private void notRespondedExistenceChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) {
            c.setState(Constraint.State.POSSIBLY_SATISFIED);
        
    	} else {
            c.setState(Constraint.State.PERMANENTLY_VIOLATED);
            if (isDConstraint(c))
                permViolatedDCon.add(toBeProcessedDataConstraint);
            else
                permViolatedCon.add(c);
        }
    	
    	//traceAndStatePrintOut(c);
    }
    
    private void notResponseChecker(Constraint c) throws DeclareParserException, Err, GenerationException {
        if (checkConstraint(c)) {
        }
    }
    
    public void setDataConstraints(List<DataConstraint> dataconstraints) {
        dataConstraints = dataconstraints;
    }


    // Printing functions
    public void traceAndStatePrintOut(Constraint c) {
    	String out = "The trace is ";
        
        switch (c.getState()) {
        case PERMANENTLY_SATISFIED:
        	out += "permanently satisfied";
            break;
        case PERMANENTLY_VIOLATED:
        	out += "permanently violated";
            break;
        case POSSIBLY_VIOLATED:
        	out += "possibly violated";
            break;
        case POSSIBLY_SATISFIED:
        	out += "possibly satisfied";
            break;
        default:
        	out += "error!";
            break;
        }
        
        out += " for the constraint " + checkedConstraintPrintOut(c);
        
        System.out.println(out);
    }
    
    private String checkedConstraintPrintOut(Constraint c) {
    	String out = c.getName();
    	
    	switch (c.getName()) {
		case "Existence":
		case "Absence":
		case "Exactly":
			if (c.isBinary())
				out += encodings.getActivityMapping().get(c.taskB())
					+ "[" + encodings.getActivityMapping().get(c.taskA()) + "]";
			else
				out += "1[" + encodings.getActivityMapping().get(c.taskA()) + "]";
			break;
		
		case "Init":
			out += "[" + encodings.getActivityMapping().get(c.taskA()) + "]";
			break;
			
		case "Choice":
		case "ExclusiveChoice":
		case "RespondedExistence":
		case "Precedence":
		case "AlternatePrecedence":
		case "ChainPrecedence":
		case "Response":
		case "AlternateResponse":
		case "ChainResponse":
		case "Succession":
		case "AlternateSuccession":
		case "ChainSuccession":
		case "NotRespondedExistence":
		case "NotPrecedence":
		case "NotChainPrecedence":
		case "NotResponse":
		case "NotChainResponse":
			out += "[" + encodings.getActivityMapping().get(c.taskA()) + ", " + encodings.getActivityMapping().get(c.taskB()) + "]";
			break;
		}
    	
    	return out;
    }
    
    // Checking constraints and stuff methods
    private boolean checkConstraint(Constraint c) throws DeclareParserException, Err, GenerationException {
    	AlloyCodeGenerator gen = new AlloyCodeGenerator(maxTraceLen, minTraceLen, bitwidth, 1, false, false, true);
        
        boolean isDataConstraint = isDConstraint(c);
        if (isDataConstraint)
            gen.runConstraintChecker(model, null, false, isDataConstraint, toBeProcessedDataConstraint);
        else
            gen.runConstraintChecker(model, c, false, isDataConstraint, null);// Ugly solution, but beggars can't be choosers.
        
        String alloyCode = gen.getAlloyCode();
        
        TraceAlloyCode tacGen = new TraceAlloyCode();
        tacGen.setNumericData(gen.getNumericData());
        tacGen.run(trace, model, isDataConstraint); // We set is data False, because this is just regular constraints.
        String traceCode = tacGen.getTraceCode();
        
        return alloyCheck(alloyCode+traceCode);
    }
    
    public boolean checkFullConjuction() throws GenerationException, DeclareParserException, Err{
        AlloyCodeGenerator gen = new AlloyCodeGenerator((maxTraceLen + 2), minTraceLen, bitwidth, 1, false, false, true);
        gen.runLogGeneration(model, false, 1, null, "monitoring");
        String alloyCode = gen.getAlloyCode();
        
        TraceAlloyCode traceGen = new TraceAlloyCode();
        traceGen.setNumericData(gen.getNumericData());
        traceGen.run(trace, model, true);
        String traceCode = traceGen.getTraceCode(); // This always same
        
        return alloyCheck(alloyCode+traceCode);
    }

    public boolean checkSublistConjunction(int n, int r) throws GenerationException, DeclareParserException, Err {
        // We do SubLists
        ArrayList<ArrayList<DataConstraint>> subLists = new ArrayList<>();
        subLists = printCombination(allConstraintArr,n, r,subLists); // See on selle täieliku subsetiga onja.
        
        boolean overAllSolution = true;
        
        for (ArrayList<DataConstraint> subList : subLists) {
            AlloyCodeGenerator gen = new AlloyCodeGenerator((maxTraceLen + 6), minTraceLen, bitwidth, 1, false, false, true);
            TraceAlloyCode traceGen = new TraceAlloyCode();
            
            boolean solution = checkSubSet(subList, traceGen, gen); // Me saame, siin kas true või false, kui on false, siis njoormilt saame asju teha nüüd.
            if (!solution) { // see on nüüd false aga mis värk on see ,et meil on siis ainult üks subList checkitud
                overAllSolution = false;
                
                int i = 0;
                while (i < subList.size()) {
                    DataConstraint dc = subList.get(i);
                    
                    if (dc.getFunctions() == null){ // We know this is a new fake dataConstraint that is actually a Constraint so we have to find a constraint that corresponds to the original one.
                        for (Constraint c : constraints) {
                            // With this if clause we try to find whether to constraints are exactly the same
                            if (c.getName().equals(dc.getName()) && c.getArgs().equals(dc.getArgs()) && c.getStatement().getCode().equals(dc.getStatement().getCode()) && c.getStatement().getLine() == dc.getStatement().getLine()) {
                                c.setState(Constraint.State.STATE_CONFLICT);
                                System.out.println("Conflictis on c: " + c.getName());
                            }
                        }
                    
                    } else {
                        dc.setState(Constraint.State.STATE_CONFLICT);
                        System.out.println("Conflictis on dc: " + dc.getName());
                    }
                    
                    i++;
                }
            }
        }
        
        return overAllSolution;
    }
    
    public boolean isDConstraint(Constraint c){
        return (c instanceof DataConstraint);
    }
    
    boolean checkSubSet(ArrayList<DataConstraint> subList, TraceAlloyCode traceGen, AlloyCodeGenerator gen) throws Err, GenerationException, DeclareParserException {
        boolean isPermViolated = false;
        boolean isSubList = false;

        for (DataConstraint dc: subList) {
            if (dc.getFunctions() == null)
                for(Constraint c : constraints)
                    if (c.getName().equals(dc.getName()) && c.getArgs().equals(dc.getArgs())
                    		&& c.getStatement().getCode().equals(dc.getStatement().getCode())
                    		&& c.getStatement().getLine() == dc.getStatement().getLine())
                        if (c.getState() == Constraint.State.PERMANENTLY_VIOLATED)
                            isPermViolated = true;
                        
            if (dc.getState() == Constraint.State.PERMANENTLY_VIOLATED)
                isPermViolated = true;
        }
        
        for (ArrayList<DataConstraint> conflictedConstraint : conflictedConstraints)
            if (subList.containsAll(conflictedConstraint))
                isSubList = true;
            
        if (!conflictedConstraints.contains(subList) && !isPermViolated && !isSubList) {
            gen.runConflictChecker(model, subList, false);
            traceGen.setNumericData(gen.getNumericData());
            traceGen.run(trace, model, true);
            String alloyCode = gen.getAlloyCode();
            String traceCode = traceGen.getTraceCode();
            String allAlloyCode = alloyCode +traceCode;
            boolean solution = (alloyCheck(allAlloyCode));
            //System.out.println("in isDConstraint");
            
            if (!solution)
                conflictedConstraints.add(subList);
            
            return solution;
        
        } else {
            return true;
        }
    }

    public boolean checkModel(int min, int max) throws GenerationException, DeclareParserException, Err {
        minTraceLen = min;
        maxTraceLen = max;
        AlloyCodeGenerator gen = new AlloyCodeGenerator(maxTraceLen, minTraceLen, bitwidth, 1, false, false, true);
        gen.runLogGeneration(model, false, 1, null, "monitoring");
        String alloyCode = gen.getAlloyCode();
        //System.out.println("in checkModel");
        
        return alloyCheck(alloyCode);
    }

    private boolean alloyCheck(String allAlloyCode) throws Err {
        writeAllText(alsFilename, allAlloyCode);
        AlloyComponent alloy = new AlloyComponent();
        Module world = alloy.parse(alsFilename);
        A4Solution solution = alloy.executeFromFile(maxTraceLen, bitwidth);
        //Global.log.accept("Found Solution: " + (solution != null && solution.satisfiable()));
        
        return solution != null && solution.satisfiable();
    }

    private static void writeAllText(String filename, String text) {
        try(  PrintWriter out = new PrintWriter( new FileWriter(filename, false) )  ){
            out.print( text );
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    //This is for when the trace is new and starts with StartDummy so we set everything to possibly satisfied;
    public String [][] initMatrix(){
        int sizeOfConstraints = constraints.size() + dataConstraints.size();
        String[][]newMatrix = new String[sizeOfConstraints][1];
        declaredMatrix = newMatrix;// Here we set that we can keep tabs on the old matrix.
        return newMatrix;
    }

    private String [][] getRealMatrix(){// We need to add another column to the old matrix so we are creating a completely new one.
        int sizeOfConstraints =constraints.size() + dataConstraints.size();
        int sizeOfTrace = (trace.size());
        String [][] realMatrix = new String[sizeOfConstraints][sizeOfTrace]; // But the size in place.
        for(int i = 0; i < declaredMatrix.length; i++){
            for (int j = 0; j<declaredMatrix[0].length; j++){
                realMatrix[i][j] = declaredMatrix[i][j];
            }
        }
        return realMatrix;
    }

    private ArrayList<ArrayList<DataConstraint>> getCombination(DataConstraint[] arr, int n, int r,
                                                  int index, DataConstraint[] data, int i, ArrayList<ArrayList<DataConstraint>> returnList)
    {
        // Current combination is ready to be printed,
        // print it
        if (index == r) {
            ArrayList<DataConstraint> arrL = new ArrayList<>();
            for (int j = 0; j < r; j++) {
                arrL.add(data[j]);
            }
            returnList.add(arrL);

            return returnList;
        }

        // When no more elements are there to put in data[]
        if (i >= n)
            return returnList;

        // current is included, put next at next
        // location
        data[index] = arr[i];
        getCombination(arr, n, r, index + 1,
                data, i + 1, returnList);

        // current is excluded, replace it with
        // next (Note that i+1 is passed, but
        // index is not changed)
        getCombination(arr, n, r, index, data, i + 1, returnList);
        return returnList;
    }

    // The main function that prints all combinations
    // of size r in arr[] of size n. This function
    // mainly uses combinationUtil()
    private ArrayList<ArrayList<DataConstraint>> printCombination(DataConstraint[] arr, int n, int r, ArrayList<ArrayList<DataConstraint>> dcList)
    {
        // A temporary array to store all combination
        // one by one
        DataConstraint data[] = new DataConstraint[r];

        // Print all combination using temprary
        // array 'data[]'
        dcList = getCombination(arr, n, r, 0, data, 0, dcList);
        return dcList;
    }

    private String[][] updateNewMatrix(){// we have done the checking before so the constraint state  should be there already.
        String [][] realMatrix = getRealMatrix();
        int i = 0;
        int lastMatrixColumnIndex = ((realMatrix[0].length) -1); // Jälle dummystart
        for (Constraint c : constraints){
            switch (c.getState()) {
                case STATE_CONFLICT:
                    realMatrix[i][lastMatrixColumnIndex] = "conflict";
                    break;
                case PERMANENTLY_SATISFIED:
                    realMatrix[i][lastMatrixColumnIndex] = "sat";
                    break;
                case PERMANENTLY_VIOLATED:
                    realMatrix[i][lastMatrixColumnIndex] = "viol";
                    break;
                case POSSIBLY_VIOLATED:
                    realMatrix[i][lastMatrixColumnIndex] = "poss.viol";
                    break;
                case POSSIBLY_SATISFIED:
                    realMatrix[i][lastMatrixColumnIndex] = "poss.sat";
                    break;
                default:
                    realMatrix[i][lastMatrixColumnIndex] = "unknown";
                    break;
            }
            i++;
        }
        for (DataConstraint dc : dataConstraints){
            switch (dc.getState()) {
                case STATE_CONFLICT:
                    realMatrix[i][lastMatrixColumnIndex] = "conflict";
                    break;
                case PERMANENTLY_SATISFIED:
                    realMatrix[i][lastMatrixColumnIndex] = "sat";
                    break;
                case PERMANENTLY_VIOLATED:
                    realMatrix[i][lastMatrixColumnIndex] = "viol";
                    break;
                case POSSIBLY_VIOLATED:
                    realMatrix[i][lastMatrixColumnIndex] = "poss.viol";
                    break;
                case POSSIBLY_SATISFIED:
                    realMatrix[i][lastMatrixColumnIndex] = "poss.sat";
                    break;
                default:
                    realMatrix[i][lastMatrixColumnIndex] = "unknown";
                    break;
            }
            i++;
        }
        declaredMatrix = realMatrix;
        return realMatrix;
    }
    
    public String updatedString(){
        String[][] updatedMatrix = updateNewMatrix();
        return getResult(updatedMatrix);
    }

    public void setConstraints(List<Constraint> constraints) {
        this.constraints = constraints;
    }
    
    public void setDataConstraintsName(String[] dataConstraintsName) {
        this.dataConstraintsName = dataConstraintsName;
    }
    
    public void setConstraintStringNames(){
        constraints = model.getConstraints();
        constraintNames = new String[constraints.size() + dataConstraintsName.length];
        
        int i = 0;
        
        for (Constraint c : constraints) {
            if (!c.isBinary())
                constraintNames[i] = c.getName() + "([" + encodings.getActivityMapping().get(c.taskA()) + "])";
            else
            	constraintNames[i] = c.getName() + "(["
										+ encodings.getActivityMapping().get(c.taskA()) + ", "
										+ encodings.getActivityMapping().get(c.taskB()) + "])";
            i++;
        }
        
        for (String s : dataConstraintsName) {
        	String inBrackets = s.substring(s.indexOf("[")+1, s.indexOf("]")); // inside [ ]
            List<String> acts = Arrays.asList( inBrackets.split(", ") );
            
            if (acts.size() == 4 || acts.size() == 3) {
                String sb = "";
                
                if (acts.size()==3 && acts.get(1).length()>acts.get(2).length())
                    sb = acts.get(0) + ", " + acts.get(1);
                
                else
                    sb = acts.get(0) + ", " + acts.get(2);
                
                s = s.replace(inBrackets, sb);
            }
            
            int ind = s.indexOf("[");
            StringBuffer newString = new StringBuffer(s);
            newString.insert(ind, "(");
            newString.append(")");
            constraintNames[i] = newString.toString();
            
            i++;
        }
    }

    public void setConflictedConstraints(ArrayList<ArrayList<DataConstraint>> conflictedConstraints) {
        this.conflictedConstraints = conflictedConstraints;
    }
    
    public void setPermViolatedDCon(ArrayList<DataConstraint> permViolatedDCon) {
        this.permViolatedDCon = permViolatedDCon;
    }

    public void setPermViolatedCon(ArrayList<Constraint> permViolatedCon) {
        this.permViolatedCon = permViolatedCon;
    }

    private String convert(XTrace trace, int pos) {
        XExtendedEvent ev = XExtendedEvent.wrap(trace.get(pos));
        return "" + ev.getTimestamp().getTime();
    }

    private String getResult(final String[][] matrix) {
        String result = "[";
        int intCounterMin;
        int intCounterMax;
        String INF = "inf";
        for (int i = 0; i < matrix.length; i++) {// We have the constraints
            intCounterMin = 0;
            intCounterMax = 0;
            String oldStatus = matrix[i][0];
            for (int j = 0; j < matrix[0].length; j++) {
                if (matrix[i][j] == null) {
                    matrix[i][j] = oldStatus;
                }
                if ((j == (matrix[0].length - 1)) && (i == (matrix.length - 1))) {
                    if (!matrix[i][j].equals(oldStatus)) {
                        result = result + "mholds_for(status(" + constraintNames[i] + "," + oldStatus + "),["
                                + convert(trace, intCounterMin) + "," + convert(trace, intCounterMax) + "]),";
                        oldStatus = matrix[i][j];
                        intCounterMin = intCounterMax;
                    }
                    result = result + "mholds_for(status(" + constraintNames[i] + "," + matrix[i][j] + "),["
                            + convert(trace, intCounterMin) + "," + INF + "])]";
                    intCounterMin = intCounterMax;
                    intCounterMax++;
                } else {
                    if (j == (matrix[0].length - 1)) {
                        if (matrix[i][j].equals(oldStatus)) {
                            result = result + "mholds_for(status(" + constraintNames[i] + "," + matrix[i][j]
                                    + "),[" + convert(trace, intCounterMin) + "," + INF + "]),";
                        } else {
                            result = result + "mholds_for(status(" + constraintNames[i] + "," + oldStatus + "),["
                                    + convert(trace, intCounterMin) + "," + convert(trace, intCounterMax)
                                    + "])," + "mholds_for(status(" + constraintNames[i] + "," + matrix[i][j]
                                    + "),[" + convert(trace, intCounterMax) + "," + INF + "]),";
                        }
                    } else {
                        if (!matrix[i][j].equals(oldStatus)) {
                            result = result + "mholds_for(status(" + constraintNames[i] + "," + oldStatus + "),["
                                    + convert(trace, intCounterMin) + "," + convert(trace, intCounterMax)
                                    + "]),";
                            oldStatus = matrix[i][j];
                            intCounterMin = intCounterMax;
                        }
                    }
                }
                intCounterMax++;
            }
        }
        return result;
    }
}

