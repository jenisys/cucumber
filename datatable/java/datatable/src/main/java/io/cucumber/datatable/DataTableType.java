package io.cucumber.datatable;

import io.cucumber.datatable.dependency.com.fasterxml.jackson.databind.JavaType;
import io.cucumber.datatable.dependency.com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.cucumber.datatable.TypeFactory.aListOf;
import static io.cucumber.datatable.TypeFactory.constructType;

/**
 * A data table type describes how a data table should be represented as an object.
 *
 * @see <a href="https://github.com/cucumber/cucumber/tree/master/datatable">DataTable - README.md</a>
 */
public final class DataTableType {

    private static final ConversionRequired CONVERSION_REQUIRED = new ConversionRequired();
    private final JavaType targetType;
    private final RawTableTransformer<?> transformer;
    private final Class<?> elementType;

    private DataTableType(Class<?> type, Type target, RawTableTransformer<?> transformer) {
        if (type == null) throw new NullPointerException("targetType cannot be null");
        if (target == null) throw new NullPointerException("target cannot be null");
        if (transformer == null) throw new NullPointerException("transformer cannot be null");
        this.elementType = type;
        this.targetType = constructType(target);
        this.transformer = transformer;
    }

    /**
     * Creates a data table type that transforms the whole table to a single object.
     *
     * @param type        the type of the object
     * @param transformer a function that creates an instance of <code>type</code> from the data table
     * @param <T>         see <code>type</code>
     */
    public <T> DataTableType(Class<T> type, TableTransformer<T> transformer) {
        this(type, type, new TableTransformerAdaptor<>(transformer));
    }

    /**
     * Creates a data table type that transforms the rows of the table into a list of objects.
     *
     * @param type        the type of the list items
     * @param transformer a function that creates an instance of <code>type</code> from the data table row
     * @param <T>         see <code>type</code>
     */
    public <T> DataTableType(Class<T> type, TableRowTransformer<T> transformer) {
        this(type, aListOf(type), new TableRowTransformerAdaptor<>(transformer));
    }

    /**
     * Creates a data table type that transforms the entries of the table into a list of objects. An entry consists
     * of the elements of the table header paired with the values of each subsequent row.
     *
     * @param type        the type of the list items
     * @param transformer a function that creates an instance of <code>type</code> from the data table entry
     * @param <T>         see <code>type</code>
     * @see DataTableType#entry(Class)
     */
    public <T> DataTableType(Class<T> type, TableEntryTransformer<T> transformer) {
        this(type, aListOf(type), new TableEntryTransformerAdaptor<>(transformer));
    }

    /**
     * Creates a data table type that transforms the cells of the table into a list of list of objects.
     *
     * @param type        the type of the list of lists items
     * @param transformer a function that creates an instance of <code>type</code> from the data table cell
     * @param <T>         see <code>type</code>
     */
    public <T> DataTableType(Class<T> type, TableCellTransformer<T> transformer) {
        this(type, aListOf(aListOf(type)), new TableCellTransformerAdaptor<>(transformer));
    }

    /**
     * Creates a data table type that transforms an entry into an object, using Jackson Databind internally.
     * The class must have an empty constructor. There must be public fields or setters matching the table headers.
     * <p>
     * Jackson annotations will be ignored. If you need those, write your own transformer.
     *
     * @param type the type of the entry
     * @param <T>  see <code>type</code>
     * @deprecated consider defining a custom transformer that uses the Jackson {@code ObjectMapper} or registering the
     * Jackson {@code ObjectMapper} through {@link DataTableTypeRegistry#setDefaultDataTableEntryTransformer(io.cucumber.datatable.TableEntryByTypeTransformer)}
     * to transform a table entry to an arbitrary object.
     */
    @Deprecated
    public static <T> DataTableType entry(final Class<T> type) {
        return new DataTableType(type, new TableEntryTransformer<T>() {
            @Override
            public T transform(Map<String, String> entry) {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.convertValue(entry, type);
            }
        });
    }

    /**
     * Creates a data table type that transforms individual cells to another type, using Jackson Databind internally.
     * <p>
     * The class must satisfy one of the following:
     * <ul>
     * <li>String constructor</li>
     * <li>static <code>fromString</code> method</li>
     * <li>static <code>valueOf</code> method</li>
     * </ul>
     *
     * @param type the type of the cell
     * @param <T>  see <code>type</code>
     * @deprecated consider defining a custom transformer that uses the Jackson {@code ObjectMapper} or registering the
     * Jackson {@code ObjectMapper} through {@link DataTableTypeRegistry#setDefaultDataTableCellTransformer(io.cucumber.datatable.TableCellByTypeTransformer)}
     * to transform a table cell to an arbitrary object.
     */
    @Deprecated
    public static <T> DataTableType cell(final Class<T> type) {
        return new DataTableType(type, new TableCellTransformer<T>() {
            @Override
            public T transform(String cell) {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.convertValue(cell, type);
            }
        });
    }

    /**
     * Creates a data table type for default cell transformer
     *
     * @param cellType                    class representing cell declared in {@code List<List<T>>}
     * @param defaultDataTableTransformer default cell transformer registered in {@link DataTableTypeRegistry#setDefaultDataTableCellTransformer(TableCellByTypeTransformer)}
     * @param <T>                         see {@code cellType}
     * @return new DataTableType witch transforms {@code List<List<String>>} to {@code List<List<T>>}
     */
    static <T> DataTableType defaultCell(final Class<T> cellType, final TableCellByTypeTransformer defaultDataTableTransformer) {
        return new DataTableType(cellType, new TableCellTransformer<T>() {
            @Override
            public T transform(String cell) throws Throwable {
                return defaultDataTableTransformer.transform(cell, cellType);
            }
        });
    }

    /**
     * Creates a data table type for default entry transformer
     *
     * @param entryType                   class representing entry declared in {@code List<T>}
     * @param defaultDataTableTransformer default entry transformer registered in {@link DataTableTypeRegistry#setDefaultDataTableEntryTransformer(TableEntryByTypeTransformer)}
     * @param <T>                         see {@code entryType}
     * @return new DataTableType witch transforms {@code List<List<String>>} to {@code List<T>}
     */
    static <T> DataTableType defaultEntry(final Class<T> entryType, final TableEntryByTypeTransformer defaultDataTableTransformer, final TableCellByTypeTransformer tableCellByTypeTransformer) {
        return new DataTableType(entryType, new TableEntryTransformer<T>() {
            @Override
            public T transform(Map<String, String> entry) throws Throwable {
                return defaultDataTableTransformer.transform(entry, entryType, tableCellByTypeTransformer);
            }
        });
    }

    public Object transform(List<List<String>> raw) {
        try {
            return transformer.transform(raw);
        } catch (Throwable throwable) {
            throw new CucumberDataTableException(
                    String.format("'%s' could not transform%n%s", toCanonical(), DataTable.create(raw)), throwable);
        }
    }

    JavaType getTargetType() {
        return targetType;
    }

    String toCanonical() {
        return targetType.toCanonical();
    }

    Class<?> getElementType() {
        return elementType;
    }

    Class<?> getTransformerType() {
        return transformer.getOriginalTransformerType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataTableType that = (DataTableType) o;

        return targetType.equals(that.targetType);
    }

    @Override
    public int hashCode() {
        return targetType.hashCode();
    }

    interface RawTableTransformer<T> {
        Class<?> getOriginalTransformerType();

        T transform(List<List<String>> raw) throws Throwable;
    }

    private static class TableCellTransformerAdaptor<T> implements RawTableTransformer<List<List<T>>> {
        private final TableCellTransformer<T> transformer;

        TableCellTransformerAdaptor(TableCellTransformer<T> transformer) {
            if (transformer == null) throw new NullPointerException("transformer cannot be null");
            this.transformer = transformer;
        }

        @Override
        public Class<?> getOriginalTransformerType() {
            return TableCellTransformer.class;
        }

        @Override
        public List<List<T>> transform(List<List<String>> raw) throws Throwable {
            List<List<T>> list = new ArrayList<>(raw.size());
            for (List<String> tableRow : raw) {
                List<T> row = new ArrayList<>(tableRow.size());
                for (String entry : tableRow) {
                    row.add(transformer.transform(entry));
                }
                list.add(row);
            }
            return list;
        }
    }

    private static class TableRowTransformerAdaptor<T> implements RawTableTransformer<List<T>> {
        private final TableRowTransformer<T> transformer;

        TableRowTransformerAdaptor(TableRowTransformer<T> transformer) {
            if (transformer == null) throw new NullPointerException("transformer cannot be null");
            this.transformer = transformer;
        }

        @Override
        public Class<?> getOriginalTransformerType() {
            return TableRowTransformer.class;
        }

        @Override
        public List<T> transform(List<List<String>> raw) throws Throwable {
            List<T> list = new ArrayList<>();
            for (List<String> tableRow : raw) {
                list.add(transformer.transform(tableRow));
            }

            return list;
        }
    }

    private static class TableEntryTransformerAdaptor<T> implements RawTableTransformer<List<T>> {
        private final TableEntryTransformer<T> transformer;

        TableEntryTransformerAdaptor(TableEntryTransformer<T> transformer) {
            if (transformer == null) throw new NullPointerException("transformer cannot be null");
            this.transformer = transformer;
        }

        @Override
        public Class<?> getOriginalTransformerType() {
            return TableEntryTransformer.class;
        }

        @Override
        public List<T> transform(List<List<String>> raw) throws Throwable {
            DataTable table = DataTable.create(raw, CONVERSION_REQUIRED);
            List<T> list = new ArrayList<>();
            for (Map<String, String> entry : table.asMaps()) {
                list.add(transformer.transform(entry));
            }

            return list;
        }
    }

    private static class TableTransformerAdaptor<T> implements RawTableTransformer<T> {
        private final TableTransformer<T> transformer;

        TableTransformerAdaptor(TableTransformer<T> transformer) {
            if (transformer == null) throw new NullPointerException("transformer cannot be null");
            this.transformer = transformer;
        }

        @Override
        public Class<?> getOriginalTransformerType() {
            return TableTransformer.class;
        }

        @Override
        public T transform(List<List<String>> raw) throws Throwable {
            return transformer.transform(DataTable.create(raw, CONVERSION_REQUIRED));
        }
    }
}
