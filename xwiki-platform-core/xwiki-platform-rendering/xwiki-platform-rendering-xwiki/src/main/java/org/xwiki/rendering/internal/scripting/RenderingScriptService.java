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
package org.xwiki.rendering.internal.scripting;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.PrintRendererFactory;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.rendering.renderer.printer.WikiPrinter;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;

/**
 * Provides Rendering-specific Scripting APIs.
 * 
 * @version $Id$
 * @since 2.3M1
 */
@Component
@Named("rendering")
@Singleton
public class RenderingScriptService implements ScriptService
{
    /**
     * Used to lookup parsers and renderers to discover available syntaxes.
     */
    @Inject
    private ComponentManager componentManager;

    /**
     * @return the list of syntaxes for which a Parser is available
     */
    public List<Syntax> getAvailableParserSyntaxes()
    {
        List<Syntax> syntaxes = new ArrayList<Syntax>();
        try {
            for (Parser parser : this.componentManager.lookupList(Parser.class)) {
                syntaxes.add(parser.getSyntax());
            }
        } catch (ComponentLookupException e) {
            // Failed to lookup parsers, consider there are no syntaxes available
        }

        return syntaxes;
    }

    /**
     * @return the list of syntaxes for which a Renderer is available
     */
    public List<Syntax> getAvailableRendererSyntaxes()
    {
        List<Syntax> syntaxes = new ArrayList<Syntax>();
        try {
            for (PrintRendererFactory factory : this.componentManager.lookupList(PrintRendererFactory.class)) {
                syntaxes.add(factory.getSyntax());
            }
        } catch (ComponentLookupException e) {
            // Failed to lookup renderers, consider there are no syntaxes available
        }

        return syntaxes;
    }

    /**
     * Parses a text written in the passed syntax.
     *
     * @param text the text to parse
     * @param syntaxId the id of the syntax in which the text is written in
     * @return the XDOM representing the AST of the parsed text or null if an error occurred
     * @since 3.2M3
     */
    public XDOM parse(String text, String syntaxId)
    {
        XDOM result;
        try {
            result = this.componentManager.lookup(Parser.class, syntaxId).parse(new StringReader(text));
        } catch (Exception e) {
            result = null;
        }
        return result;
    }

    /**
     * Render a list of Blocks into the passed syntax.
     *
     * @param block the block to render
     * @param outputSyntaxId the syntax in which to render the blocks
     * @return the string representing the passed blocks in the passed syntax or null if an error occurred
     * @since 3.2M3
     */
    public String render(Block block, String outputSyntaxId)
    {
        String result;
        WikiPrinter printer = new DefaultWikiPrinter();
        try {
            this.componentManager.lookup(BlockRenderer.class, outputSyntaxId).render(block, printer);
            result = printer.toString();
        } catch (Exception e) {
            result = null;
        }
        return result;
    }
}
