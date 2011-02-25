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
package org.xwiki.rendering.internal.macro.dashboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.container.ContainerMacroParameters;
import org.xwiki.rendering.macro.dashboard.AbstractDashboardRenderer;
import org.xwiki.rendering.macro.dashboard.Gadget;
import org.xwiki.rendering.transformation.MacroTransformationContext;

/**
 * Specialized gadget that interprets its position as a pair of numbers, the first being the index of the column, and
 * the second being the index of the gadget inside the column.
 * 
 * @version $Id$
 * @since 3.0M3
 */
class ColumnGadget extends Gadget
{
    /**
     * The index of the column of this gadget.
     */
    private Integer column;

    /**
     * The index of this gadget inside its column.
     */
    private Integer index;

    /**
     * Creates a column gadget using the passed title, content and position.
     * 
     * @param id the id of this gadget
     * @param title the title of the gadget
     * @param content the content of the gadget
     * @param position the position of the gadget
     */
    public ColumnGadget(String id, List<Block> title, List<Block> content, String position)
    {
        super(id, title, content, position);
    }

    /**
     * Creates a column gadget which is a copy of the passed gadget.
     * 
     * @param gadget the gadget to copy into a column gadget.
     */
    public ColumnGadget(Gadget gadget)
    {
        this(gadget.getId(), gadget.getTitle(), gadget.getContent(), gadget.getPosition());
    }

    /**
     * @return the column
     */
    public Integer getColumn()
    {
        return column;
    }

    /**
     * @return the index
     */
    public Integer getIndex()
    {
        return index;
    }

    @Override
    public void setPosition(String position)
    {
        super.setPosition(position);

        // parse the position as a "container, index" pair and store the container number and index. <br />
        // TODO: move this code in an API class since the comma separated format is more generic than the columns layout
        // split by comma, first position is column, second position is index
        String[] split = position.split(",");
        try {
            this.column = new Integer(split[0].trim());
            this.index = new Integer(split[1].trim());
        } catch (ArrayIndexOutOfBoundsException e) {
            // nothing, just leave column and index null. Not layoutable in columns
        } catch (NumberFormatException e) {
            // same, nothing, just leave column and index null. Not layoutable in columns
        }
    }
}

/**
 * The columns dashboard renderer, that renders the list of passed gadgets in columns, and interprets the positions as
 * pairs of column number and gadget index.
 * 
 * @version $Id$
 * @since 3.0M3
 */
@Component("columns")
public class ColumnsDashboardRenderer extends AbstractDashboardRenderer
{
    /**
     * The component manager, to inject to the {@link BlocksContainerMacro}.
     */
    @Requirement
    private ComponentManager componentManager;

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.macro.dashboard.DashboardRenderer#renderGadgets(java.util.List,
     *      MacroTransformationContext)
     */
    public List<Block> renderGadgets(List<Gadget> gadgets, MacroTransformationContext context)
        throws MacroExecutionException
    {
        // transform the passed gagdets in a list of column gadgets
        List<ColumnGadget> columnGadgets = new ArrayList<ColumnGadget>();
        List<Gadget> invalidGadgets = new ArrayList<Gadget>();
        for (Gadget gadget : gadgets) {
            ColumnGadget cGadget = new ColumnGadget(gadget);
            if (cGadget.getColumn() != null && cGadget.getIndex() != null) {
                columnGadgets.add(cGadget);
            } else {
                invalidGadgets.add(gadget);
            }
        }

        // sort the column gadgets by first the column number then by index in column, for all those which have a valid
        // position
        Collections.sort(columnGadgets, new Comparator<ColumnGadget>()
        {
            public int compare(ColumnGadget g1, ColumnGadget g2)
            {
                return g1.getColumn().equals(g2.getColumn()) ? g1.getIndex() - g2.getIndex() : g1.getColumn()
                    - g2.getColumn();
            }
        });

        // get the maximmum column number in the gadgets list and create that number of columns. This is the column
        // number of the last gadget in the ordered list
        int columns = 0;
        if (!columnGadgets.isEmpty()) {
            columns = columnGadgets.get(columnGadgets.size() - 1).getColumn();
        }

        // create the list of gadget containers
        List<Block> gadgetContainers = new ArrayList<Block>();
        for (int i = 0; i < columns; i++) {
            GroupBlock gContainer = new GroupBlock();
            gContainer.setParameter(CLASS, DashboardMacro.GADGET_CONTAINER);
            // and generate the ids of the gadget containers, which are column numbers, 1 based
            gContainer.setParameter(ID, DashboardMacro.GADGET_CONTAINER_PREFIX + (i + 1));
            gadgetContainers.add(gContainer);
        }

        // render them as columns using the container macro and appropriate parameters
        ContainerMacroParameters containerParams = new ContainerMacroParameters();
        containerParams.setLayoutStyle("columns");
        containerParams.setJustify(true);
        BlocksContainerMacro containerMacro = new BlocksContainerMacro();
        containerMacro.setComponentManager(componentManager);
        containerMacro.setContent(gadgetContainers);
        List<Block> layoutedResult = containerMacro.execute(containerParams, null, context);

        for (ColumnGadget gadget : columnGadgets) {
            int columnIndex = gadget.getColumn() - 1;
            gadgetContainers.get(columnIndex).addChildren(decorateGadget(gadget));
        }

        // and return the result
        return layoutedResult;
    }
}