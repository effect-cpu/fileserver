package com.milktea.fileserver;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class DocStore {

    public static class Doc {
        public String docId;
        public String name;
        public String mime;
        public boolean dir;
        public long size = -1;
        public long modified;

        public boolean isIndex() {
            return !dir && name != null
                    && (name.equalsIgnoreCase("index.html") || name.equalsIgnoreCase("index.htm"));
        }
    }

    private static final String[] COLS = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    };

    private final ContentResolver cr;
    private final Uri treeUri;
    private final String rootId;
    private String rootName = "";

    public DocStore(ContentResolver cr, Uri treeUri) {
        this.cr = cr;
        this.treeUri = treeUri;
        this.rootId = DocumentsContract.getTreeDocumentId(treeUri);
    }

    public Uri getTreeUri() {
        return treeUri;
    }

    public String getRootId() {
        return rootId;
    }

    public String getRootName() {
        return rootName;
    }


    public String loadRootName() {
        try {
            Doc d = stat(rootId);
            rootName = d.name;
        } catch (Exception e) {
            rootName = rootId;
        }
        return rootName;
    }

    public Doc stat(String docId) throws IOException {
        Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
        Cursor c = cr.query(uri, COLS, null, null, null);
        if (c == null) throw new IOException("无法读取文档");
        try {
            if (!c.moveToFirst()) throw new IOException("文档不存在");
            return readRow(c);
        } finally {
            c.close();
        }
    }

    public List<Doc> children(String docId) throws IOException {
        List<Doc> out = new ArrayList<Doc>();
        Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId);
        Cursor c = cr.query(uri, COLS, null, null, null);
        if (c == null) return out;
        try {
            while (c.moveToNext()) out.add(readRow(c));
        } finally {
            c.close();
        }
        sort(out);
        return out;
    }

    public Doc findChild(String dirDocId, String name) throws IOException {
        List<Doc> list = children(dirDocId);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(name)) return list.get(i);
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equalsIgnoreCase(name)) return list.get(i);
        }
        return null;
    }

    public InputStream open(String docId) throws IOException {
        Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new IOException("无法打开文件");
        return in;
    }


    public Doc resolve(String relPath) throws IOException {
        String[] segs = splitPath(relPath);
        if (segs == null) return null;
        Doc cur = stat(rootId);
        for (int i = 0; i < segs.length; i++) {
            if (!cur.dir) return null;
            Doc next = findChild(cur.docId, segs[i]);
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }


    public static String[] splitPath(String p) {
        if (p == null) return new String[0];
        String[] raw = p.split("/");
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < raw.length; i++) {
            String s = raw[i];
            if (s.length() == 0 || s.equals(".")) continue;
            if (s.equals("..")) return null;
            out.add(s);
        }
        return out.toArray(new String[out.size()]);
    }

    private Doc readRow(Cursor c) {
        Doc d = new Doc();
        d.docId = c.getString(0);
        d.name = c.isNull(1) ? "" : c.getString(1);
        d.mime = c.isNull(2) ? "" : c.getString(2);
        d.dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(d.mime);
        d.size = c.isNull(3) ? -1 : c.getLong(3);
        d.modified = c.isNull(4) ? 0 : c.getLong(4);
        return d;
    }

    private static void sort(List<Doc> out) {
        Collections.sort(out, new Comparator<Doc>() {
            public int compare(Doc a, Doc b) {
                if (a.dir != b.dir) return a.dir ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }
}
