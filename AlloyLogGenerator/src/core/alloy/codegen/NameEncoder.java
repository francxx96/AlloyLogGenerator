package core.alloy.codegen;

import core.Global;
import core.helpers.RandomHelper;
import declare.DeclareParser;
import declare.DeclareParserException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Vasiliy on 2018-05-10.
 */
public class NameEncoder {
    private DeclareParser parser;
    
    private Map<String, String> activityMapping;
    private Map<String, String> traceAttributeMapping;
    private Set<DataMappingElement> dataMapping;

    public NameEncoder(DeclareParser parser) {
        this.parser = parser;
    }

    public String encode(String declare) throws DeclareParserException {
    	String[] declareLines = parser.splitStatements(declare);
        
        List<String> dataLines = new ArrayList<>();
        for (String line : declareLines)
        	if (parser.isData(line))
        		dataLines.add(line);
        
        String encodedDeclare = "";
        activityMapping = new HashMap<>();
        traceAttributeMapping = new HashMap<>();
        dataMapping = new HashSet<>();
        
        for (String line : declareLines) {
        	String encodedLine = "";
        	
        	if (parser.isTraceAttribute(line)) {
    			String name = line.substring("trace ".length()).trim();
        		String encoding = RandomHelper.getName();
        		
        		checkInterference(declare, name);
    			traceAttributeMapping.put(encoding, name);
    			
    			encodedLine = "trace " + encoding;
    		
        	
        	} else if (parser.isActivity(line)) {
        		String name = line.substring("activity ".length()).trim();
        		String encoding = RandomHelper.getName();
        		
    			checkInterference(declare, name);
    			activityMapping.put(encoding, name);
    			
    			encodedLine = "activity " + encoding;
        	
        	
        	} else if (parser.isDataBinding(line)) {
        		line = line.substring(5);
        		
        		for (Map.Entry<String, String> e : activityMapping.entrySet()) {
        			String actName = e.getValue();
        			if (line.startsWith(actName+": ")) {
        				/*
        				String dataNamesStr = line.substring((actName+": ").length());
        				List<String> possibleDataNames = extractAllPossibleDataNames(dataNamesStr);
        				List<String> trueNames = new ArrayList<>();
        				*/
        				List<String> trueNames = Arrays.asList(line.substring((actName+": ").length()).split(",\\s+"));
        				trueNames.forEach(name -> { checkInterference(declare, name); });
        				
        				for (String dl : dataLines) {
        					for (String name : trueNames) {	//for (String possibleName : possibleDataNames) {
        						if (dl.startsWith(name+": ")) {
    								//trueNames.add(possibleName);
        							List<String> values = Arrays.asList( dl.substring((name+": ").length()).split(", ") );
    								values.forEach(value -> { value.trim(); });
    								DataMappingElement payload = new DataMappingElement(name, new HashSet<>(values));
    								dataMapping.add(payload);
        						}
        					}
        				}
    					
        				List<String> trueEncodings = new ArrayList<>();
        				for (DataMappingElement dme : dataMapping)
        					if (trueNames.contains(dme.getOriginalName()))
        						trueEncodings.add(dme.getEncodedName());
        					
        				encodedLine = "bind " + e.getKey() + ": " + String.join(", ", trueEncodings);
        				break;
        			}
        		}
        	
        		
        	} else if (parser.isData(line)) {
        		for (DataMappingElement dme : dataMapping)
        			if (line.startsWith(dme.getOriginalName()+": ")) {
        				encodedLine = dme.getEncodedName() + ": " + String.join(", ", dme.getValuesMapping().keySet());
        				break;
        			}
        	
        	
        	} else if (parser.isDataConstraint(line)) {
        		// Encoding templates of the data constraint
            	Pattern templatePattern = Pattern.compile(".*\\[.*\\]");
            	Matcher mTempl = templatePattern.matcher(line);
            	if (mTempl.find()) {
            		String templates = mTempl.group();
            		encodedLine += templates.substring(0, templates.indexOf('[')+1);
            		String[] activities = templates.substring(templates.indexOf('[')+1, templates.lastIndexOf(']')).split(",\\s+");
            		
            		List<String> encodedActivityNames = new ArrayList<>();
            		for (String act : activities)
            			for (Map.Entry<String, String> e : activityMapping.entrySet())
            				if (e.getValue().equals(act))
            					encodedActivityNames.add(e.getKey());
            		
            		encodedLine += String.join(", ", encodedActivityNames) + "]";
            		line = line.substring(templates.length()).trim();
            	}
            	
            	// Encoding condition of the data constraint
            	Pattern conditionPattern = Pattern.compile("\\|[^\\|]*");
            	Matcher mCond = conditionPattern.matcher(line);
            	while (mCond.find()) {
            		String singleCondition = mCond.group().substring(1);	// Removing the initial pipe character
            		encodedLine += "|" + encodeCondition(singleCondition) + " ";
            	}
        	
        	
        	} else if (parser.isConstraint(line)) {
        		continue;	// ???????????????????????????????????
        	}
        	
        	encodedDeclare += encodedLine.isEmpty() ? "" : encodedLine + "\n" ;
        }
        
        return encodedDeclare;
    }
    
    private String encodeCondition(String condition) throws DeclareParserException {
    	String encodedCondition = "";
    	Pattern parenthesesPattern = Pattern.compile("(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2$)");
    	
    	String lastFound = "Logical Operator";
    	while(!condition.isBlank()) {
    		condition = condition.trim();
    		
    		if (lastFound.equals("Logical Operator")) {
				lastFound = "Predicate";
				
				if (condition.charAt(0) == '(') {	// It implies the presence of a predicate in parentheses
					Matcher m = parenthesesPattern.matcher(condition);
					if (m.find()) {
						encodedCondition += "(" + encodeCondition(condition.substring(m.start()+1, m.end()-1)) + ")";
						condition = condition.substring( m.end()<condition.length() ? m.end()+1 : m.end() );
					}
					
				} else {
					AbstractMap.SimpleEntry<String, String> mapping = encodeNextPredicate(condition);
					encodedCondition += " " + mapping.getKey() + " ";
					condition = condition.substring(mapping.getValue().length());
				}
				
			} else {	// The last string found in the condition should be a predicate
				lastFound = "Logical Operator";
				
				if (condition.toLowerCase().startsWith("and")) {
					encodedCondition += " and ";
					condition = condition.substring(3);
				} else if (condition.toLowerCase().startsWith("or")) {
					encodedCondition += " or ";
					condition = condition.substring(2);
				}
			}
    	}
    	
    	return encodedCondition.replaceAll("\\s+", " ").trim();
    }
    
    private AbstractMap.SimpleEntry<String, String> encodeNextPredicate(String condition) throws DeclareParserException {
    	// Dealing with unary predicates   <==>   UnaryOperator AttributeName
    	if (condition.indexOf(' ') >= 0
			&& condition.substring(0, condition.indexOf(' ')).matches("(?i)same|different|not|exist") 
			&& dataMapping.stream().anyMatch(item -> condition.substring(condition.indexOf(' ')).trim().startsWith(item.getOriginalName()) )) {
    		
    		String unaryOperator = condition.substring(0, condition.indexOf(' '));
    		DataMappingElement attributeMapping = dataMapping.stream()
    								.filter(item -> condition.substring(condition.indexOf(' ')).trim().startsWith(item.getOriginalName()))
    								.findFirst().get();
    		
    		return new AbstractMap.SimpleEntry<>
    								(unaryOperator + " " + attributeMapping.getEncodedName(),
    								 unaryOperator + " " + attributeMapping.getOriginalName());
    	
    	// Dealing with binary predicates   <==>   (A|B).AttributeName BinaryOperator AttributeValue|(AttrVal1, ..., AttrValN)
    	} else if ( dataMapping.stream().anyMatch(item -> condition.substring(2).startsWith(item.getOriginalName())) ) {
    		
    		// There can be more than one attribute matching the initial part of the condition, the one with the longest name will be selected
    		List<DataMappingElement> matchedAttributes = dataMapping.stream()
									.filter(item -> condition.substring(2).startsWith(item.getOriginalName()))
									.collect(Collectors.toList());
    		
    		DataMappingElement longestAttribute = matchedAttributes.stream()
				    				.map(item -> new AbstractMap.SimpleEntry<>(item, item.getOriginalName().length()) )
				    				.max( Comparator.comparingInt(AbstractMap.SimpleEntry::getValue) )
				    				.get().getKey();
    		
    		String attributeName = condition.substring(0, 2) + longestAttribute.getOriginalName();
    		String encodedName = condition.substring(0, 2) + longestAttribute.getEncodedName();
    		
    		String reducedCondition = condition.substring(attributeName.length());
    		
    		boolean blankBeforeOperator = reducedCondition.charAt(0) == ' ';
    		reducedCondition = reducedCondition.trim();
    			
    		String operator = getOperatorFromCondition(reducedCondition);
    		
    		reducedCondition = reducedCondition.substring(operator.length());
    		
    		boolean blankAfterOperator = reducedCondition.charAt(0) == ' ';
    		reducedCondition = reducedCondition.trim();
    		
    		if (operator.matches("(?i)(not )?in")) {
	    		List<String> attributeValues = getValuesInSet(reducedCondition);
	    		
	    		List<String> encodedValues = new ArrayList<>();
	    		for (String value : attributeValues)
	    			for (Map.Entry<String, String> e : longestAttribute.getValuesMapping().entrySet())
	    				if (e.getValue().equals(value))
	    					encodedValues.add(e.getKey());
	    		
	    		return new AbstractMap.SimpleEntry<>
										(encodedName+" "+operator+" "+"("+String.join(", ", encodedValues)+")",
					    				 attributeName+(blankBeforeOperator?" ":"")+operator+(blankAfterOperator?" ":"")+"("+String.join(", ", attributeValues)+")");
    		
    		} else {
    			String attributeValue, encodedValue;
    			
    			if (longestAttribute.isNumeric()) {
    				Pattern numPattern = Pattern.compile("-?\\d+(\\.\\d+)?");
    				Matcher m = numPattern.matcher(reducedCondition);
    				m.find();
					String number = m.group();
					
					attributeValue = number;
					encodedValue = number;
    				
    			} else {
    				String temp = reducedCondition;
    	    		Map.Entry<String, String> valueMapping = longestAttribute.getValuesMapping().entrySet().stream()
    	    													.filter(entry -> temp.startsWith(entry.getValue()))
    	    													.findFirst().get();
    	    		
    	    		attributeValue = valueMapping.getValue();
    	    		encodedValue = valueMapping.getKey();
    			}
    			
	    		return new AbstractMap.SimpleEntry<>
					    				(encodedName+" "+operator+" "+encodedValue, 
					    				 attributeName+(blankBeforeOperator?" ":"")+operator+(blankAfterOperator?" ":"")+attributeValue);
    		}
    	}
    	
    	throw new DeclareParserException("An error is occurred while encoding a predicate in a condition");
    }
    
    private String getOperatorFromCondition(String condition) throws DeclareParserException {
    	condition = condition.replaceAll("\\s+", " ");
    	StringBuilder charBuffer = new StringBuilder();
    	
    	int charInd=0;
    	while (charInd < condition.length()) {
    		charBuffer.append(condition.charAt(charInd));
    		String op = charBuffer.toString();
    		
    		if (op.matches("(?i)=|!=|(not )?in")) {
				return op;
				
			} else if (op.matches("<|>")) {
				if (charInd+1 < condition.length()) {
					op += condition.charAt(charInd+1);
					
					if (op.matches("<=|>="))
						return op;
					else
						return charBuffer.toString();
				
				} else
					return op;
				
			} else if (op.matches("(?i)is")) {
				if (charInd+4 < condition.length()) {
					for (int i=charInd+1; i<=charInd+4; i++)
						op += condition.charAt(i);
					
					if (op.matches("(?i)is not"))
						return op;
					else
						return charBuffer.toString();
					
				} else
					return op;
			}
    		
    		charInd++;
    	}
    	
    	throw new DeclareParserException("No operator was found while parsing a declare condition.");
    }
    
    private List<String> getValuesInSet(String condition) {
    	int parenthesesBalance = 0;
    	
    	int charInd = 0;
    	for (char ch : condition.toCharArray()) {
    		if (ch == '(')
    			parenthesesBalance++;
    		else if (ch == ')')
    			parenthesesBalance--;
    		
    		if (parenthesesBalance == 0)
    			break;
    		
    		charInd++;
    	}
    	
    	String valuesStr = condition.substring(1, charInd);
    	List<String> values = Arrays.asList(valuesStr.split(", ")); 
    	values.forEach(val -> { val.trim(); });
    	
    	return values;
    }
    
    /*
     * With this part I tried to improve the name extraction from .decl files by overcoming the limitations in names,
     * e.g. the fact that only names without ", ", ": " and "|" char sequences are allowed.
     * But it is incomplete...
    
    private List<String> extractAllPossibleDataNames(String dataNamesStr) {
    	Set<String> possibleNames = new HashSet<>();
    	
    	possibleNames.add(dataNamesStr);	// The whole string can be a single data name
    	
    	List<String> singleNames = Arrays.asList(dataNamesStr.split(", "));
    	
    	if (singleNames.size() > 1) {
	    	possibleNames.addAll(singleNames);
	    	
	    	for (String[] pair : getNameDispositions(singleNames))
	    		possibleNames.addAll(Arrays.asList(pair));
    	}
    	
    	return new ArrayList<>(possibleNames);
    }
    
    private Set<String[]> getNameDispositions(List<String> singleNames) {
    	Set<String[]> dispositions = new HashSet<>();
    	
    	List<String> firstNames = new ArrayList<>();
    	while (!singleNames.isEmpty()) {
    		firstNames.add(singleNames.remove(0));
    		String[] pair = new String[] {String.join(", ", firstNames), String.join(", ", singleNames) };
    		dispositions.add(pair);
    	}
    	
    	return dispositions;
    }
    */
    
    // may throw exception if name might interfere with reserved keywords
    private void checkInterference(String declare, String name) {
        //String keywords = IOHelper.readAllText("./data/keywords.txt");
        String keywords = " activity x\n" +
                " Init[]\n" +
                " Existence[] \n" +
                " Existence[]\n" +
                " Absence[]\n" +
                " Absence[]\n" +
                " Exactly[]\n" +
                " Choice[] \n" +
                " ExclusiveChoice[] \n" +
                " RespondedExistence[] \n" +
                " Response[] \n" +
                " AlternateResponse[] \n" +
                " ChainResponse[]\n" +
                " Precedence[] \n" +
                " AlternatePrecedence[] \n" +
                " ChainPrecedence[] \n" +
                " NotRespondedExistence[] \n" +
                " NotResponse[] \n" +
                " NotPrecedence[] \n" +
                " NotChainResponse[]\n" +
                " NotChainPrecedence[]\n" +
                " integer between x and x\n" +
                " float between x and x\n" +
                " trace x\n" +
                " bind x\n" +
                " : , x\n" +
                " is not x\n" +
                " not in x\n" +
                " is x\n" +
                " in x\n" +
                " not x\n" +
                " or x\n" +
                " and x\n" +
                " same x\n" +
                " different x\n" +
                " not x\n" +
                " [ ] x\n" +
                " ( ) x\n" +
                " . x\n" +
                " > x\n" +
                " < x\n" +
                " >= x\n" +
                " <= x\n" +
                " = x\n" +
                " | x\n" +
                "\n";
        
        //for (String name : names) {
        
	        if (keywords.contains(name))
	            Global.log.accept("The name '" + name + "' might be part of reserved keyword. If other errors appear try to rename it or use in quote marks.");
	
	        if (Global.deepNamingCheck) {
	            Pattern pattern = Pattern.compile("[\\d\\w]" + name + "[\\d\\w]|[\\d\\w]" + name + "|" + name + "[\\d\\w]");
	            Matcher m = pattern.matcher(declare);
	            if (m.find() && !name.contains(m.group(0)))
	                Global.log.accept("The name '" + name + "' might be part of reserved keyword. If other errors appear try to rename it or use in quote marks.");
	        }
	        
        //}
    }
    
    public Map<String, String> getActivityMapping() {
		return activityMapping;
	}

	public Map<String, String> getTraceAttributeMapping() {
		return traceAttributeMapping;
	}

	public Set<DataMappingElement> getDataMapping() {
		return dataMapping;
	}

	public class DataMappingElement {
    	String originalName;
    	String encodedName;
    	Map<String, String> valuesMapping;
    	boolean isNumeric;
    	
    	public DataMappingElement(String originalName, Set<String> values) {
    		this.originalName = originalName;
    		this.encodedName = RandomHelper.getName();
    		this.valuesMapping = new HashMap<>();
    		
    		if (values.size() == 1 && values.iterator().next().matches("(integer|float) between \\d+(\\.\\d+)? and \\d+(\\.\\d+)?")) {
    			this.valuesMapping.put(values.iterator().next(), values.iterator().next());
    			this.isNumeric = true;
    			
    		} else {
    			for (String val : values)
        			this.valuesMapping.put(RandomHelper.getName(), val);
    			
    			this.isNumeric = false;
    		}
    	}

		public String getOriginalName() {
			return originalName;
		}

		public String getEncodedName() {
			return encodedName;
		}

		public Map<String, String> getValuesMapping() {
			return valuesMapping;
		}
		
		public boolean isNumeric() {
			return isNumeric;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((originalName == null) ? 0 : originalName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			DataMappingElement other = (DataMappingElement) obj;
			if (originalName == null) {
				if (other.originalName != null)
					return false;
			} else if (!originalName.equals(other.originalName))
				return false;
			return true;
		}
    }
}
