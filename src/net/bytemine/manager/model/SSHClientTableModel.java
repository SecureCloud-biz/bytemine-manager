/*************************************************************************
 * Written by / Copyright (C) 2009-2012 bytemine GmbH                     *
 * Author: Daniel Rauer                     E-Mail:    rauer@bytemine.net *
 *                                                                        *
 * http://www.bytemine.net/                                               *
 *************************************************************************/

package net.bytemine.manager.model;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.Vector;
import java.util.stream.IntStream;

import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import net.bytemine.manager.Constants;
import net.bytemine.manager.i18n.ResourceBundleMgmt;
import net.bytemine.utility.StringUtils;


/**
 * A table model for the ssh client table
 *
 * @author Daniel Rauer
 */
public class SSHClientTableModel extends AbstractTableModel {

    private static final long serialVersionUID = -5940231640180913764L;

    private ResourceBundle rb = ResourceBundleMgmt.getInstance().getUserBundle();

    private int sortCol = 0;
    private boolean isSortAsc = true;
    private boolean isMac = false;

    // the column names to display    
    private String[] columnNames = {rb.getString("ssh_client.overview.column1"),
            rb.getString("ssh_client.overview.column2"),
            rb.getString("ssh_client.overview.column2a"),
            rb.getString("ssh_client.overview.column3"),
            rb.getString("ssh_client.overview.column4"),
            rb.getString("ssh_client.overview.column5")
    };

    // contains the data of a table row
    private Vector<String[]> rowData = new Vector<>();

    // map in which row numbers and usernames are stored as key-value-pairs
    private static HashMap<String, String> nameRowMapping;


    public SSHClientTableModel(Vector<String[]> data) {
        reloadData(data);
    }


    /**
     * Reinitializes the model by translating the column names
     * using the users resource bundle
     */
    private void reinit() {
        rb = ResourceBundleMgmt.getInstance().getUserBundle();
        // third column is a MAC address, not an IP
        columnNames = isMac ? new String[]{rb.getString("ssh_client.overview.column1"),
                rb.getString("ssh_client.overview.column2"),
                rb.getString("ssh_client.overview.column2b"),
                rb.getString("ssh_client.overview.column3"),
                rb.getString("ssh_client.overview.column4"),
                rb.getString("ssh_client.overview.column5")
        } : new String[]{rb.getString("ssh_client.overview.column1"),
                rb.getString("ssh_client.overview.column2"),
                rb.getString("ssh_client.overview.column2a"),
                rb.getString("ssh_client.overview.column3"),
                rb.getString("ssh_client.overview.column4"),
                rb.getString("ssh_client.overview.column5")
        };
    }


    /**
     * Reloads the data from the database
     */
    public void reloadData(Vector<String[]> data) {
        rowData = data;

        // value is not an IP but a MAC address
        if (!data.isEmpty() && StringUtils.isMACAddress(data.get(0)[2])) isMac = true;

        refreshMapping();
        
        if (isMac) {
            reinit();
            fireTableStructureChanged();
        }
        fireTableDataChanged();
    }

    
    /**
     * Refresh the mapping
     */
    private void refreshMapping() {
        int rowNr = 0;
        for (String[] row : rowData) {
            this.addNameRowMapping(rowNr + "", row[0]);
            rowNr++;
        }
    }
    

    public int getRowCount() {
        return rowData.size();
    }


    public String getColumnName(int col) {
        String columnName = columnNames[col];
        if (col == sortCol)
            columnName += isSortAsc ? " <" : " >";
        return columnName;
    }


    public int getColumnCount() {
        return columnNames.length;
    }


    public Object getValueAt(int row, int col) {
        String[] rowStr = rowData.get(row);
        if (col == 3 || col == 4) {
            String bytes = rowStr[col];
            return StringUtils.formatBytes(bytes);
        }
        return rowStr[col];
    }


    public HashMap<String, String> getNameRowMapping() {
        return nameRowMapping;
    }

    public void setNameRowMapping(HashMap<String, String> nameRowMap) {
        nameRowMapping = nameRowMap;
    }

    private void addNameRowMapping(String key, String value) {
        if (nameRowMapping == null)
            nameRowMapping = new HashMap<>();
        nameRowMapping.put(key, value);
    }


    public class ColumnListener extends MouseAdapter {
        protected JTable table;

        public ColumnListener(JTable t) {
            table = t;
        }

        public void mouseClicked(MouseEvent e) {
            TableColumnModel colModel = table.getColumnModel();
            int columnModelIndex = colModel.getColumnIndexAtX(e.getX());
            int modelIndex = colModel.getColumn(columnModelIndex)
                    .getModelIndex();

            if (modelIndex < 0)
                return;
            if (sortCol == modelIndex)
                isSortAsc = !isSortAsc;
            else
                sortCol = modelIndex;

            IntStream.range(0, getColumnCount()).mapToObj(colModel::getColumn).forEach(column -> column.setHeaderValue(getColumnName(column.getModelIndex())));
            table.getTableHeader().repaint();

            rowData.sort(new SSHClientComparator(isSortAsc, sortCol));
            // refresh the mapping
            refreshMapping();
            
            table.tableChanged(new TableModelEvent(
                    SSHClientTableModel.this));
            table.repaint();
        }
    }
}


class SSHClientComparator implements Comparator<String[]> {
    private boolean isSortAsc;
    private int sortCol;

    SSHClientComparator(boolean sortAsc, int sortCol) {
        this.isSortAsc = sortAsc;
        this.sortCol = sortCol;
    }

    public int compare(String[] s1, String[] s2) {
        int result;
        String i1_str = s1[sortCol];
        String i2_str = s2[sortCol];
        if (i1_str == null)
            i1_str = "";
        if (i2_str == null)
            i2_str = "";
        
        Date d1 = null;
        Date d2 = null;
        try {
            d1 = Constants.getShowFormatForCurrentLocale().parse(i1_str);
            d2 = Constants.getShowFormatForCurrentLocale().parse(i2_str);
        } catch(Exception e) {
            try {
                d1 = Constants.getDetailedFormatForCurrentLocale().parse(i1_str);
                d2 = Constants.getDetailedFormatForCurrentLocale().parse(i2_str);
            } catch(Exception e2) {}
        }

        // compare the dates
        if (StringUtils.isDigit(i1_str) && StringUtils.isDigit(i2_str)) {
            Integer i1 = Integer.parseInt(i1_str);
            Integer i2 = Integer.parseInt(i2_str);
            result = i1.compareTo(i2);
        } else result = d1 != null && d2 != null ? d1.compareTo(d2) : i1_str.compareTo(i2_str);


        if (!isSortAsc)
            result = -result;
        return result;
    }


    public boolean equals(Object obj) {
        if (obj instanceof SSHClientComparator) {
            SSHClientComparator compObj = (SSHClientComparator) obj;
            return compObj.isSortAsc == isSortAsc;
        }
        return false;
    }

}