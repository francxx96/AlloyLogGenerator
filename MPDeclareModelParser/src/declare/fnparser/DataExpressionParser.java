package declare.fnparser;

import declare.DeclareParserException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Vasiliy on 2017-10-19.
 */
public class DataExpressionParser {
    private String opTokenRegex = "(?i)(\\s+(is\\s+not|is|not\\s+in|in|or|and))|(\\s*(not|same|different|exist))\\s+"; 	// (?i) means case-insensitive
    private Pattern opTokenPattern = Pattern.compile(opTokenRegex);
    
    private String numTokenRegex = "-?\\d+(\\.\\d+)?";
    private Pattern numTokenPattern = Pattern.compile(numTokenRegex);
    
    private String varTokenRegex = "(?!"+numTokenRegex+")\\S+\\.\\S+";
    private Pattern varTokenPattern = Pattern.compile(varTokenRegex);
    
    private String taskTokenRegex = "\\S+";
    private Pattern taskTokenPattern = Pattern.compile(taskTokenRegex);
    
    private String groupTokenRegex = "(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2$)";
    private Pattern groupTokenPattern = Pattern.compile(groupTokenRegex);
    
    private String compTokenRegex = "<=|>=|<|>|=|!=";
    private Pattern compTokenPattern = Pattern.compile(compTokenRegex);
    
    private String placeholderRegex = "\\?";
    private Pattern placeholderPattern = Pattern.compile(placeholderRegex);

    private Pattern tokenPattern = Pattern.compile(groupTokenRegex + "|" + opTokenRegex + "|" + compTokenRegex  
												+ "|" + numTokenRegex + "|" + varTokenRegex + "|" + taskTokenRegex 
												+ "|" + placeholderRegex);

    public DataExpression parse(String condition) throws DeclareParserException {
    	List<Token> tokens = parseTokens(condition);
        return buildExpressionTree(tokens);
    }

    private List<Token> parseTokens(String conditionString) throws DeclareParserException {
        List<Token> tokens = new ArrayList<>();
        
        int index = 0;
        Matcher m = tokenPattern.matcher(conditionString);
        
        while (m.find())
        	tokens.add(createToken(index++, m.group()));
        
        for (Token t : tokens)
        	if (t.getType() == Token.Type.Group)
        		if (t.getValue().chars().filter(ch -> ch == '(').count() != t.getValue().chars().filter(ch -> ch == ')').count())
                    throw new DeclareParserException("Unbalanced parentheses in token: \"" + t.getValue() + "\" of condition \"" + conditionString + "\"");	// TODO: write erroneous line of code

        return tokens;
    }
    
    private Token createToken(int i, String value) throws DeclareParserException {
    	// Order matters!

    	if (groupTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Group, value);
    	
    	if (opTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Operator, value.trim().toLowerCase());
    	
    	if (compTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Comparator, value);
    	
    	if (numTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Number, value);
    	
    	if (varTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Variable, value);
    	
    	if (taskTokenPattern.matcher(value).matches())
            return new Token(i, Token.Type.Activity, value);
    	
    	if (placeholderPattern.matcher(value).matches())
            return new Token(i, Token.Type.R, value);
    	
        throw new DeclareParserException("unknown token: " + value);    // TODO: write erroneous line of code
    }
    
    private DataExpression buildExpressionTree(List<Token> tokens) throws DeclareParserException {
        
    	if (tokens.isEmpty())	// Empty expression evaluates to true
            return new ValueExpression(new Token(0, Token.Type.Activity, "True[]"));   
    	
    	tokens = unrollNotEqualsTokens(tokens);
    	
    	// Parsing "and", "or" logical operators at first
    	for (Token tkn : tokens) {
    		switch (tkn.getValue().toLowerCase().replaceAll("\\s+"," ").strip()) {
    		case "or":
    		case "and":
    			return new BinaryExpression(tkn, getLeft(tokens, tkn.position), getRight(tokens, tkn.position));
    		}
    	}
    	
    	// Then, parsing comparators and remaining operators
        for (Token tkn : tokens) {
        	if (tkn.getType() == Token.Type.Comparator) {
        		return new BinaryExpression(tkn, getLeft(tokens, tkn.position), getRight(tokens, tkn.position));
        	
        	} else if (tkn.getType() == Token.Type.Operator) {
        		switch (tkn.getValue().toLowerCase().replaceAll("\\s+"," ").strip()) {
        		// Unary operators
        		case "not":
        		case "same":
        		case "different":
        		case "exist":
        			return new UnaryExpression(tkn, getRight(tokens, tkn.position));
        		
        		// Binary operators
        		case "is":
        		case "is not":
        		case "in":
        		case "not in":
        			return new BinaryExpression(tkn, getLeft(tokens, tkn.position), getRight(tokens, tkn.position));
        			
        		default:
        			throw new DeclareParserException("Unhandled token operator: " + tkn.getValue());
				}
        	}
        }
        
        String setTokenRegex = "\\((\\S+,\\s+)*\\S+\\)";
        // Parsing remaining tokens at last
        for (Token tkn : tokens) {
        	switch (tkn.getType()) {
			case Group:
				// Since Group type matches everything is surrounded by parentheses, it matches also Set type.
	        	// So, before recursion, it has to be checked if the Group is nothing more than a Set.
				if (tkn.getValue().matches(setTokenRegex))
					return new ValueExpression(new Token(tkn.getPosition(), Token.Type.Set, tkn.getValue()));
				
				// Recursive call
				return parse(tkn.getValue().substring(1, tkn.getValue().length()-1)); // Cutting the surrounding parentheses
			
			case Activity:
			case Number:
			case Variable:
			case R:
				return new ValueExpression(tkn);

			default:
				throw new DeclareParserException("Unhandled token type: " + tkn.getType());
			}
        }

        throw new DeclareParserException(String.join(", ", tokens.stream().map(Object::toString).collect(Collectors.toList())));
    }
    
    private List<Token> unrollNotEqualsTokens(List<Token> tokens) {
    	List<Token> notEqTokens = tokens.stream()
    									.filter(tkn -> tkn.getType().equals(Token.Type.Comparator) && tkn.getValue().equals("!="))
    									.collect(Collectors.toList());
    	
    	/*
    	 * A "!=" token can be seen as a conjunction of two tokens "<" and ">" with the same operands.
    	 * For example: 				(x != 1)    <==>    (x < 1) OR (x > 1)
    	 */
    	if (!notEqTokens.isEmpty()) {
    		// Iterating in reversed order to maintain the valid position references of the single not-equals tokens
    		for (Token neqTkn : notEqTokens) {
    			Token precOperand = tokens.get(tokens.indexOf(neqTkn)-1);
    			Token succOperand = tokens.get(tokens.indexOf(neqTkn)+1);
    			
    			// Grouping the unrolled tokens
    			Token conj = new Token(0, Token.Type.Group, "(" + precOperand.getValue() + " < " + succOperand.getValue() + " or "
    															+ precOperand.getValue() + " > " + succOperand.getValue() + ")" );
    			tokens.set(tokens.indexOf(precOperand), conj);
    			tokens.remove(tokens.indexOf(neqTkn));
    			tokens.remove(tokens.indexOf(succOperand));
    		}
    		
    		// Updating the token positions
	        tokens.forEach(elem -> elem.setPosition(tokens.indexOf(elem)) );
    	}
    	
    	return tokens;
    }

    private DataExpression getLeft(List<Token> tokens, int position) throws DeclareParserException {
        return buildExpressionTree(tokens.subList(0, position));
    }

    private DataExpression getRight(List<Token> tokens, int position) throws DeclareParserException {
        List<Token> sub = tokens.subList(position + 1, tokens.size());
        sub.forEach(i -> i.setPosition(i.getPosition() - position - 1));
        return buildExpressionTree(sub);
    }

    public void retrieveNumericExpressions(Map<String, List<DataExpression>> map, DataExpression expr) throws DeclareParserException {
        if (expr.getNode().getType() == Token.Type.Comparator) {
        	BinaryExpression binExpr = (BinaryExpression) expr;
        	
        	if (binExpr.getLeft().getNode().getType() == Token.Type.Number
        			^ binExpr.getRight().getNode().getType() == Token.Type.Number) {
        		
        		String var;
        		if (binExpr.getLeft().getNode().getType() == Token.Type.Number)
        			var = binExpr.getRight().getNode().getValue();
        		else
        			var = binExpr.getLeft().getNode().getValue();
        		
        		var = var.substring(var.indexOf('.') + 1);
                if (!map.containsKey(var))
                    map.put(var, new ArrayList<>());
                map.get(var).add(expr);
        		
                return;
        	}
        }

        if (expr instanceof UnaryExpression) {
            retrieveNumericExpressions(map, ((UnaryExpression) expr).getValue());

        } else if (expr instanceof BinaryExpression) {
            retrieveNumericExpressions(map, ((BinaryExpression) expr).getLeft());
            retrieveNumericExpressions(map, ((BinaryExpression) expr).getRight());
        }
    }
}
