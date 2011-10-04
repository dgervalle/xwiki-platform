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

package com.xpn.xwiki.store.migration.hibernate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.DeletedAttachment;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.doc.XWikiLink;
import com.xpn.xwiki.doc.rcs.XWikiRCSNodeInfo;
import com.xpn.xwiki.store.XWikiHibernateBaseStore.HibernateCallback;
import com.xpn.xwiki.store.migration.DataMigrationException;
import com.xpn.xwiki.store.migration.XWikiDBVersion;

/**
 * Migration for XWIKI-6990 Reduce the likelihood of having same (hibernate) document id for different documents.
 * This data migration convert document ID to a new hash algorithm.
 *
 * @version $Id$
 */
@Component
@Named("R32999XWIKI6990")
public class R32999XWIKI6990DataMigration extends AbstractHibernateDataMigration
{
    @Override
    public String getDescription()
    {
        return "Convert document IDs to use the new improved hash algorithm.";
    }

    @Override
    public XWikiDBVersion getVersion()
    {
        return new XWikiDBVersion(32999);
    }

    @Override
    public void hibernateMigrate() throws DataMigrationException, XWikiException
    {
        // migrate data
        getStore().executeWrite(getXWikiContext(), true, new HibernateCallback<Object>()
        {
            @Override @SuppressWarnings("unchecked")
            public Object doInHibernate(Session session) throws HibernateException, XWikiException
            {
                try {
                    Map<Long, Long> docs = new HashMap<Long, Long>();
                    String docClass = XWikiDocument.class.getName();
                    String database = getXWikiContext().getDatabase();

                    session.flush();

                    for (Object[] result : (List<Object[]>) (session.createQuery(
                        "select doc.id, doc.space, doc.name, doc.defaultLanguage, doc.language from " + docClass +
                            " as doc")
                        .list()))
                    {
                        long oldId = (Long) result[0];
                        String space = (String) result[1];
                        String name = (String) result[2];
                        String defaultLanguage = (String) result[3];
                        String language = (String) result[4];

                        XWikiDocument doc =
                            new XWikiDocument(new DocumentReference(database, space, name));
                        doc.setDefaultLanguage(defaultLanguage);
                        doc.setLanguage(language);
                        docs.put(oldId, doc.getId());
                    }

                    session.clear();

                    int count = docs.size() + 1;
                    while (!docs.isEmpty() && count > docs.size()) {
                        count = docs.size();
                        for (Iterator<Map.Entry<Long, Long>> it = docs.entrySet().iterator(); it.hasNext(); ) {
                            Map.Entry<Long, Long> entry = it.next();
                            long oldId = entry.getKey();
                            long newId = entry.getValue();

                            if (oldId == newId) {
                                it.remove();
                                continue;
                            }

                            if (!docs.containsKey(newId)) {

                                session
                                    .createQuery(
                                        "update " + docClass + " doc set doc.id = :newid where doc.id = :oldid")
                                    .setLong("newid", newId)
                                    .setLong("oldid", oldId)
                                    .executeUpdate();

                                session.createQuery(
                                    "update " + XWikiRCSNodeInfo.class.getName()
                                        + " rcs set rcs.id.docId =:newid where rcs.id.docId =:oldid")
                                    .setLong("newid", newId)
                                    .setLong("oldid", oldId)
                                    .executeUpdate();

                                session.createQuery(
                                    "update " + XWikiLink.class.getName()
                                        + " link set link.docId = :newid where link.docId = :oldid")
                                    .setLong("newid", newId)
                                    .setLong("oldid", oldId)
                                    .executeUpdate();

                                session.createQuery(
                                    "update " + XWikiAttachment.class.getName()
                                        + " att set att.docId = :newid where att.docId = :oldid")
                                    .setLong("newid", newId)
                                    .setLong("oldid", oldId)
                                    .executeUpdate();

                                session.createQuery(
                                    "update " + DeletedAttachment.class.getName()
                                        + " att set att.docId = :newid where att.docId = :oldid")
                                    .setLong("newid", newId)
                                    .setLong("oldid", oldId)
                                    .executeUpdate();

                                it.remove();
                            }
                        }
                    }

                    if (!docs.isEmpty()) {
                        throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                            XWikiException.ERROR_XWIKI_STORE_MIGRATION,
                            "Unresolved circular reference during id migration");
                    }
                } catch (Exception e) {
                    throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                        XWikiException.ERROR_XWIKI_STORE_MIGRATION, getName() + " migration failed", e);
                }
                return null;
            }
        });
    }
}
