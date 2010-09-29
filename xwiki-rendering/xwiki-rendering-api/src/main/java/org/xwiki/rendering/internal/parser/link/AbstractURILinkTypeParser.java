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
package org.xwiki.rendering.internal.parser.link;

import org.xwiki.rendering.listener.Link;
import org.xwiki.rendering.parser.LinkTypeParser;

/**
 * Default link type parser for URIs: just take the full reference as the Link reference. Note that this parser doesn't
 * extract the scheme from the URI for the link reference.
 *
 * @version $Id$
 * @since 2.5M2
 */
public abstract class AbstractURILinkTypeParser implements LinkTypeParser
{
    /**
     * {@inheritDoc}
     *
     * @see LinkTypeParser#parse(String)
     */
    public Link parse(String reference)
    {
        Link resultLink = new Link();
        resultLink.setTyped(true);
        resultLink.setType(getType());
        resultLink.setReference(reference);
        return resultLink;
    }
}