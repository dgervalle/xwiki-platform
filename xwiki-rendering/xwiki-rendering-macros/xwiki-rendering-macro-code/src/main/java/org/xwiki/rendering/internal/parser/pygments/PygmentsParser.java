/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.rendering.internal.parser.pygments;

import java.io.IOException;
import java.io.Reader;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyUnicode;
import org.python.util.PythonInterpreter;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.NewLineBlock;
import org.xwiki.rendering.block.VerbatimBlock;
import org.xwiki.rendering.parser.AbstractHighlightParser;
import org.xwiki.rendering.parser.HighlightParser;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.syntax.SyntaxType;
import org.xwiki.rendering.parser.Parser;

/**
 * Highlight provided source using Pygments.
 * 
 * @version $Id$
 * @since 1.7RC1
 */
// Note that we force the Component annotation so that this component is only registered as a Highlight Parser
// and not a Parser too since we don't want this parser to be visible to users as a valid standard input parser
// component.
@Component(roles = {HighlightParser.class })
public class PygmentsParser extends AbstractHighlightParser implements Initializable
{
    /**
     * The name of the lexer variable in PPython code.
     */
    private static final String PY_LEXER_VARNAME = "lexer";

    /**
     * The name of the formatter variable in PPython code.
     */
    private static final String PY_FORMATTER_VARNAME = "formatter";

    /**
     * The name of the listener variable in PPython code.
     */
    private static final String PY_LISTENER_VARNAME = "listener";

    /**
     * The name of the variable containing the source code to highlight in PPython code.
     */
    private static final String PY_CODE_VARNAME = "code";

    /**
     * Try part of the lexer initialization.
     */
    private static final String PY_LEXER_TRY = PY_LEXER_VARNAME + " = None\ntry:\n" + "  " + PY_LEXER_VARNAME;

    /**
     * Catch part of the lexer initialization.
     */
    private static final String PY_LEXER_CATCH = "\nexcept ClassNotFound:\n  pass";

    /**
     * Python code to create the lexer.
     */
    private static final String PY_LEXER_CREATE = PY_LEXER_TRY
        + " = pygments.lexers.get_lexer_by_name(\"{0}\", stripnl=False)" + PY_LEXER_CATCH;

    /**
     * Python code to find the lexer from source.
     */
    private static final String PY_LEXER_FIND = PY_LEXER_TRY + " = guess_lexer(code, stripnl=False)" + PY_LEXER_CATCH;

    /**
     * The syntax identifier.
     */
    private Syntax syntax;

    /**
     * The Python interpreter used to execute Pygments.
     */
    private PythonInterpreter pythonInterpreter;

    /**
     * Used to parse Pygment token values into blocks.
     */
    @Requirement("plain/1.0")
    private Parser plainTextParser;

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.component.phase.Initializable#initialize()
     */
    public void initialize() throws InitializationException
    {
        String highlightSyntaxId = getSyntaxId() + "-highlight";
        this.syntax = new Syntax(new SyntaxType(highlightSyntaxId, highlightSyntaxId), "1.0");

        this.pythonInterpreter = new PythonInterpreter();

        // imports Pygments
        this.pythonInterpreter.exec("import pygments" + "\nfrom pygments.lexers import guess_lexer"
            + "\nfrom pygments.util import ClassNotFound" + "\nfrom pygments.formatters.xdom import XDOMFormatter");
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.parser.Parser#getSyntax()
     */
    public Syntax getSyntax()
    {
        return this.syntax;
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.parser.HighlightParser#highlight(java.lang.String, java.io.Reader)
     */
    public List<Block> highlight(String syntaxId, Reader source) throws ParseException
    {
        String code;
        try {
            code = IOUtils.toString(source);
        } catch (IOException e) {
            throw new ParseException("Failed to read source", e);
        }

        if (code.length() == 0) {
            return Collections.emptyList();
        }

        List<Block> blocks = highlight(syntaxId, code);

        // TODO: there is a bug in Pygments that makes it always put a newline at the end of the content, should be
        // fixed in Pygments 1.3.
        if (code.charAt(code.length() - 1) != '\n' && !blocks.isEmpty()
            && blocks.get(blocks.size() - 1) instanceof NewLineBlock) {
            blocks.remove(blocks.size() - 1);
        }

        return blocks;
    }

    /**
     * Return a highlighted version of the provided content.
     * 
     * @param syntaxId the identifier of the source syntax.
     * @param code the content to highlight.
     * @return the highlighted version of the provided source.
     * @throws ParseException the highlighting failed.
     */
    private List<Block> highlight(String syntaxId, String code) throws ParseException
    {
        PythonInterpreter interpreter = getPythonInterpreter();
        BlocksGeneratorPygmentsListener listener = new BlocksGeneratorPygmentsListener(this.plainTextParser);

        interpreter.set(PY_LISTENER_VARNAME, listener);
        interpreter.set(PY_CODE_VARNAME, new PyUnicode(code));

        if (!StringUtils.isEmpty(syntaxId)) {
            interpreter.exec(MessageFormat.format(PY_LEXER_CREATE, syntaxId));
        } else {
            interpreter.exec(PY_LEXER_FIND);
        }

        PyObject lexer = interpreter.get(PY_LEXER_VARNAME);
        if (lexer == null || lexer == Py.None) {
            // No lexer found
            if (getLogger().isDebugEnabled()) {
                getLogger().debug("no lexer found");
            }

            return Collections.<Block> singletonList(new VerbatimBlock(code, true));
        }

        interpreter.exec(MessageFormat.format("{0} = XDOMFormatter({1})", PY_FORMATTER_VARNAME, PY_LISTENER_VARNAME));
        interpreter.exec(MessageFormat.format("pygments.highlight({0}, {1}, {2})", PY_CODE_VARNAME, PY_LEXER_VARNAME,
            PY_FORMATTER_VARNAME));

        List<String> vars = Arrays.asList(PY_LISTENER_VARNAME, PY_CODE_VARNAME, PY_LEXER_VARNAME, PY_FORMATTER_VARNAME);
        for (String var : vars) {
            interpreter.exec("del " + var);
        }

        return listener.getBlocks();
    }

    /**
     * @return the python interpreter.
     */
    protected PythonInterpreter getPythonInterpreter()
    {
        return this.pythonInterpreter;
    }
}