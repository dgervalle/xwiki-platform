package com.xpn.xwiki.store;

import java.util.Iterator;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Query;
import org.hibernate.Session;
import org.xwiki.component.annotation.Component;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiAttachmentContent;
import com.xpn.xwiki.doc.XWikiDocument;

@Component
@Named("hibernate")
@Singleton
public class XWikiHibernateAttachmentStore extends XWikiHibernateBaseStore implements XWikiAttachmentStoreInterface
{
    private static final Log log = LogFactory.getLog(XWikiHibernateAttachmentStore.class);

    /**
     * This allows to initialize our storage engine. The hibernate config file path is taken from xwiki.cfg or directly
     * in the WEB-INF directory.
     * 
     * @param xwiki
     * @param context
     * @deprecated 1.6M1. Use ComponentManager.lookup(XWikiAttachmentStoreInterface.class) instead.
     */
    @Deprecated
    public XWikiHibernateAttachmentStore(XWiki xwiki, XWikiContext context)
    {
        super(xwiki, context);
    }

    /**
     * @see #XWikiHibernateAttachmentStore(XWiki, XWikiContext)
     * @deprecated 1.6M1. Use ComponentManager.lookup(XWikiAttachmentStoreInterface.class) instead.
     */
    @Deprecated
    public XWikiHibernateAttachmentStore(XWikiContext context)
    {
        this(context.getWiki(), context);
    }

    /**
     * Initialize the storage engine with a specific path This is used for tests.
     * 
     * @param hibpath
     * @deprecated 1.6M1. Use ComponentManager.lookup(XWikiAttachmentStoreInterface.class) instead.
     */
    @Deprecated
    public XWikiHibernateAttachmentStore(String hibpath)
    {
        super(hibpath);
    }

    /**
     * Empty constructor needed for component manager.
     */
    public XWikiHibernateAttachmentStore()
    {
    }

    public void saveAttachmentContent(XWikiAttachment attachment, XWikiContext context, boolean bTransaction)
        throws XWikiException
    {
        saveAttachmentContent(attachment, true, context, bTransaction);
    }

    public void saveAttachmentContent(XWikiAttachment attachment, boolean parentUpdate, XWikiContext context,
        boolean bTransaction) throws XWikiException
    {
        try {
            XWikiAttachmentContent content = attachment.getAttachment_content();
            if (bTransaction) {
                checkHibernate(context);
                bTransaction = beginTransaction(context);
            }
            Session session = getSession(context);

            String db = context.getDatabase();
            String attachdb = (attachment.getDoc() == null) ? null : attachment.getDoc().getDatabase();
            try {
                if (attachdb != null) {
                    context.setDatabase(attachdb);
                }

                Query query =
                    session.createQuery("select attach.id from XWikiAttachmentContent as attach where attach.id = :id");
                query.setLong("id", content.getId());
                if (query.uniqueResult() == null) {
                    session.save(content);
                } else {
                    session.update(content);
                }

                if (attachment.getAttachment_archive() == null) {
                    attachment.loadArchive(context);
                }
                // The archive has been updated in XWikiHibernateStore.saveAttachment()
                context.getWiki().getAttachmentVersioningStore().saveArchive(attachment.getAttachment_archive(),
                    context, false);

                if (parentUpdate) {
                    context.getWiki().getStore().saveXWikiDoc(attachment.getDoc(), context, true);
                }

            } finally {
                context.setDatabase(db);
            }

            if (bTransaction) {
                endTransaction(context, true);
            }
        } catch (Exception e) {
            Object[] args = {attachment.getFilename(), attachment.getDoc().getFullName()};
            throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                XWikiException.ERROR_XWIKI_STORE_HIBERNATE_SAVING_ATTACHMENT,
                "Exception while saving attachment {0} of document {1}", e, args);
        } finally {
            try {
                if (bTransaction) {
                    endTransaction(context, false);
                }
            } catch (Exception e) {
            }
        }

    }

    public void saveAttachmentsContent(List<XWikiAttachment> attachments, XWikiDocument doc, boolean bParentUpdate,
        XWikiContext context, boolean bTransaction) throws XWikiException
    {
        if (attachments == null) {
            return;
        }
        try {
            if (bTransaction) {
                checkHibernate(context);
                bTransaction = beginTransaction(context);
            }
            Iterator<XWikiAttachment> it = attachments.iterator();
            while (it.hasNext()) {
                XWikiAttachment att = it.next();
                saveAttachmentContent(att, false, context, false);
            }
            if (bParentUpdate) {
                context.getWiki().getStore().saveXWikiDoc(doc, context, false);
            }
        } catch (Exception e) {
            throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                XWikiException.ERROR_XWIKI_STORE_HIBERNATE_SAVING_ATTACHMENT, "Exception while saving attachments", e);
        } finally {
            try {
                if (bTransaction) {
                    endTransaction(context, false);
                }
            } catch (Exception e) {
            }
        }

    }

    public void loadAttachmentContent(XWikiAttachment attachment, XWikiContext context, boolean bTransaction)
        throws XWikiException
    {
        try {
            if (bTransaction) {
                checkHibernate(context);
                bTransaction = beginTransaction(false, context);
            }
            Session session = getSession(context);

            String db = context.getDatabase();
            String attachdb = (attachment.getDoc() == null) ? null : attachment.getDoc().getDatabase();
            try {
                if (attachdb != null) {
                    context.setDatabase(attachdb);
                }
                XWikiAttachmentContent content = new XWikiAttachmentContent(attachment);
                attachment.setAttachment_content(content);
                session.load(content, new Long(content.getId()));

                // Hibernate calls setContent which causes isContentDirty to be true. This is not what we want.
                content.setContentDirty(false);

            } finally {
                context.setDatabase(db);
            }

            if (bTransaction) {
                endTransaction(context, false, false);
            }
        } catch (Exception e) {
            Object[] args = {attachment.getFilename(), attachment.getDoc().getFullName()};
            throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                XWikiException.ERROR_XWIKI_STORE_HIBERNATE_LOADING_ATTACHMENT,
                "Exception while loading attachment {0} of document {1}", e, args);
        } finally {
            try {
                if (bTransaction) {
                    endTransaction(context, false, false);
                }
            } catch (Exception e) {
            }
        }
    }

    public void deleteXWikiAttachment(XWikiAttachment attachment, XWikiContext context, boolean bTransaction)
        throws XWikiException
    {
        deleteXWikiAttachment(attachment, true, context, bTransaction);
    }

    public void deleteXWikiAttachment(XWikiAttachment attachment, boolean parentUpdate, XWikiContext context,
        boolean bTransaction) throws XWikiException
    {
        try {
            if (bTransaction) {
                checkHibernate(context);
                bTransaction = beginTransaction(context);
            }

            Session session = getSession(context);

            String db = context.getDatabase();
            String attachdb = (attachment.getDoc() == null) ? null : attachment.getDoc().getDatabase();
            try {
                if (attachdb != null) {
                    context.setDatabase(attachdb);
                }

                // Delete the three attachment entries
                try {
                    loadAttachmentContent(attachment, context, false);
                    try {
                        session.delete(attachment.getAttachment_content());
                    } catch (Exception e) {
                        if (log.isWarnEnabled()) {
                            log.warn("Error deleting attachment content " + attachment.getFilename() + " of doc "
                                + attachment.getDoc().getFullName());
                        }
                    }
                } catch (Exception e) {
                    if (log.isWarnEnabled()) {
                        log.warn("Error loading attachment content when deleting attachment "
                            + attachment.getFilename() + " of doc " + attachment.getDoc().getFullName());
                    }
                }

                context.getWiki().getAttachmentVersioningStore().deleteArchive(attachment, context, false);

                try {
                    session.delete(attachment);
                } catch (Exception e) {
                    if (log.isWarnEnabled()) {
                        log.warn("Error deleting attachment meta data " + attachment.getFilename() + " of doc "
                            + attachment.getDoc().getFullName());
                    }
                }

            } finally {
                context.setDatabase(db);
            }

            try {
                if (parentUpdate) {
                    List<XWikiAttachment> list = attachment.getDoc().getAttachmentList();
                    for (int i = 0; i < list.size(); i++) {
                        XWikiAttachment attach = list.get(i);
                        if (attachment.getFilename().equals(attach.getFilename())) {
                            list.remove(i);
                            break;
                        }
                    }
                    context.getWiki().getStore().saveXWikiDoc(attachment.getDoc(), context, false);
                }
            } catch (Exception e) {
                if (log.isWarnEnabled()) {
                    log.warn("Error updating document when deleting attachment " + attachment.getFilename()
                        + " of doc " + attachment.getDoc().getFullName());
                }
            }

            if (bTransaction) {
                endTransaction(context, true);
            }
        } catch (Exception e) {
            Object[] args = {attachment.getFilename(), attachment.getDoc().getFullName()};
            throw new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                XWikiException.ERROR_XWIKI_STORE_HIBERNATE_DELETING_ATTACHMENT,
                "Exception while deleting attachment {0} of document {1}", e, args);
        } finally {
            try {
                if (bTransaction) {
                    endTransaction(context, false);
                }
            } catch (Exception e) {
            }
        }
    }
}
