package parsing;

import core.exceptions.GenerationException;
import core.alloy.codegen.AlloyCodeGenerator;
import declare.DeclareModel;
import declare.DeclareParser;
import declare.DeclareParserException;
import declare.fnparser.DataExpressionParser;
import org.testng.annotations.Test;

/**
 * Created by Vasiliy on 2017-10-23.
 */
public class ExpressionParserTest {
    DataExpressionParser parser = new DataExpressionParser();
    AlloyCodeGenerator gen = new AlloyCodeGenerator(1, 1, 3, 0, true, false, true);

    @Test(expectedExceptions = DeclareParserException.class)
    public void testSpellingError() throws DeclareParserException, GenerationException {
        DeclareModel model = new DeclareParser().parse("Choiced[A,B]\n");
        gen.runLogGeneration(model, false, 1, null, "log_generation");
    }

    @Test(expectedExceptions = DeclareParserException.class)
    public void testSpellingErrorWithData() throws DeclareParserException, GenerationException {
        DeclareModel model = new DeclareParser().parse("Choise[A,B]||\n");
        gen.runLogGeneration(model, false, 1, null, "log_generation");
    }
}
