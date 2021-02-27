package com.mercari.solution.util;

import com.google.cloud.ByteArray;
import com.google.protobuf.*;
import com.google.protobuf.Duration;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.Timestamps;
import com.google.type.*;
import org.apache.beam.sdk.schemas.logicaltypes.EnumerationType;

import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtoUtil {

    public enum ProtoType {

        // https://github.com/googleapis/googleapis/tree/master/google/type
        // https://github.com/protocolbuffers/protobuf/tree/master/src/google/protobuf

        DATE("google.type.Date"),
        TIME("google.type.TimeOfDay"),
        DATETIME("google.type.DateTime"),
        LATLNG("google.type.LatLng"),
        ANY("google.protobuf.Any"),
        TIMESTAMP("google.protobuf.Timestamp"),
        //DURATION("google.protobuf.Duration"),
        BOOL_VALUE("google.protobuf.BoolValue"),
        STRING_VALUE("google.protobuf.StringValue"),
        BYTES_VALUE("google.protobuf.BytesValue"),
        INT32_VALUE("google.protobuf.Int32Value"),
        INT64_VALUE("google.protobuf.Int64Value"),
        UINT32_VALUE("google.protobuf.UInt32Value"),
        UINT64_VALUE("google.protobuf.UInt64Value"),
        FLOAT_VALUE("google.protobuf.FloatValue"),
        DOUBLE_VALUE("google.protobuf.DoubleValue"),
        NULL_VALUE("google.protobuf.NullValue"),
        EMPTY("google.protobuf.Empty"),
        CUSTOM("__custom__");

        private final String className;

        public static ProtoType of(final String classFullName) {
            for(final ProtoType type : values()) {
                if(type.className.equals(classFullName)) {
                    return type;
                }
            }
            return CUSTOM;
        }

        ProtoType(final String className) {
            this.className = className;
        }
    }

    public static Map<String, Descriptors.Descriptor> getDescriptors(final byte[] bytes) {
        try {
            final DescriptorProtos.FileDescriptorSet set = DescriptorProtos.FileDescriptorSet
                    .parseFrom(bytes);
            return getDescriptors(set);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Descriptors.Descriptor> getDescriptors(final InputStream is) {
        try {
            final DescriptorProtos.FileDescriptorSet set = DescriptorProtos.FileDescriptorSet
                    .parseFrom(is);
            return getDescriptors(set);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static DynamicMessage convert(final Descriptors.Descriptor messageDescriptor,
                                         final byte[] bytes) {
        try {
            return DynamicMessage
                    .newBuilder(messageDescriptor)
                    .mergeFrom(bytes)
                    .build();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonFormat.Printer createJsonPrinter(final Map<String, Descriptors.Descriptor> descriptors) {
        final JsonFormat.TypeRegistry.Builder builder = JsonFormat.TypeRegistry.newBuilder();
        descriptors.values().forEach(builder::add);
        return JsonFormat.printer().usingTypeRegistry(builder.build());
    }

    public static Object getValue(final DynamicMessage message,
                                  final String fieldName,
                                  final JsonFormat.Printer printer) {

        final Descriptors.FieldDescriptor field = getField(message, fieldName);
        final Object value = message.getField(field);

        boolean isNull = value == null;

        switch (field.getJavaType()) {
            case BOOLEAN:
                return isNull ? false : (Boolean)value;
            case STRING:
                return isNull ? "" : (String)value;
            case INT:
                return isNull ? 0 : (Integer)value;
            case LONG:
                return isNull ? 0 : (Long)value;
            case FLOAT:
                return isNull ? 0f : (Float)value;
            case DOUBLE:
                return isNull ? 0d : (Double)value;
            case ENUM: {
                return isNull ? new EnumerationType.Value(0) : new EnumerationType.Value(((Descriptors.EnumValueDescriptor)value).getIndex());
            }
            case BYTE_STRING:
                return isNull ? ByteArray.copyFrom("").toByteArray() : ((ByteString) value).toByteArray();
            case MESSAGE: {
                final Object object = convertBuildInValue(field.getMessageType().getFullName(), (DynamicMessage) value);
                isNull = object == null;
                switch (ProtoType.of(field.getMessageType().getFullName())) {
                    case BOOL_VALUE:
                        return !isNull && ((BoolValue) object).getValue();
                    case BYTES_VALUE:
                        return isNull ? ByteArray.copyFrom ("").toByteArray() : ((BytesValue) object).getValue().toByteArray();
                    case STRING_VALUE:
                        return isNull ? "" : ((StringValue) object).getValue();
                    case INT32_VALUE:
                        return isNull ? 0 : ((Int32Value) object).getValue();
                    case INT64_VALUE:
                        return isNull ? 0 : ((Int64Value) object).getValue();
                    case UINT32_VALUE:
                        return isNull ? 0 : ((UInt32Value) object).getValue();
                    case UINT64_VALUE:
                        return isNull ? 0 : ((UInt64Value) object).getValue();
                    case FLOAT_VALUE:
                        return isNull ? 0f : ((FloatValue) object).getValue();
                    case DOUBLE_VALUE:
                        return isNull ? 0d : ((DoubleValue) object).getValue();
                    case DATE: {
                        if(isNull) {
                            return null;
                        }
                        final Date date = (Date) object;
                        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
                    }
                    case TIME: {
                        if(isNull) {
                            return null;
                        }
                        final TimeOfDay timeOfDay = (TimeOfDay) object;
                        return LocalTime.of(timeOfDay.getHours(), timeOfDay.getMinutes(), timeOfDay.getSeconds(), timeOfDay.getNanos());
                    }
                    case DATETIME: {
                        if(isNull) {
                            return null;
                        }
                        final DateTime dt = (DateTime) object;
                        long epochMilli = LocalDateTime.of(
                                dt.getYear(), dt.getMonth(), dt.getDay(),
                                dt.getHours(), dt.getMinutes(), dt.getSeconds(), dt.getNanos())
                                .atOffset(ZoneOffset.ofTotalSeconds((int)dt.getUtcOffset().getSeconds()))
                                .toInstant()
                                .toEpochMilli();
                        return org.joda.time.Instant.ofEpochMilli(epochMilli);
                    }
                    case TIMESTAMP:
                        if(isNull) {
                            return null;
                        }
                        return org.joda.time.Instant.ofEpochMilli(Timestamps.toMillis((Timestamp) object));
                    case ANY: {
                        if(isNull) {
                            return "";
                        }
                        final Any any = (Any) object;
                        try {
                            return printer.print(any);
                        } catch (InvalidProtocolBufferException e) {
                            return any.getValue().toStringUtf8();
                        }
                    }
                    case LATLNG: {
                        if(isNull) {
                            return null;
                        }
                        final LatLng ll = (LatLng) object;
                        return String.format("%f,%f", ll.getLatitude(), ll.getLongitude());
                    }
                    case EMPTY:
                    case NULL_VALUE:
                        return null;
                    case CUSTOM:
                    default:
                        return object;
                }
            }
            default:
                return null;
        }
    }

    public static Object convertBuildInValue(final String typeFullName, final DynamicMessage value) {
        if(value.getAllFields().size() == 0) {
            return null;
        }
        switch (ProtoType.of(typeFullName)) {
            case DATE: {
                Integer year = 0;
                Integer month = 0;
                Integer day = 0;
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if(entry.getValue() == null) {
                        continue;
                    }
                    if("year".equals(entry.getKey().getName())) {
                        year = (Integer) entry.getValue();
                    } else if("month".equals(entry.getKey().getName())) {
                        month = (Integer) entry.getValue();
                    } else if("day".equals(entry.getKey().getName())) {
                        day = (Integer) entry.getValue();
                    }
                }
                return Date.newBuilder().setYear(year).setMonth(month).setDay(day).build();
            }
            case TIME: {
                Integer hours = 0;
                Integer minutes = 0;
                Integer seconds = 0;
                Integer nanos = 0;
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if(entry.getValue() == null) {
                        continue;
                    }
                    if("hours".equals(entry.getKey().getName())) {
                        hours = (Integer) entry.getValue();
                    } else if("minutes".equals(entry.getKey().getName())) {
                        minutes = (Integer) entry.getValue();
                    } else if("seconds".equals(entry.getKey().getName())) {
                        seconds = (Integer) entry.getValue();
                    } else if("nanos".equals(entry.getKey().getName())) {
                        nanos = (Integer) entry.getValue();
                    }
                }
                return TimeOfDay.newBuilder().setHours(hours).setMinutes(minutes).setSeconds(seconds).setNanos(nanos).build();
            }
            case DATETIME: {
                Integer year = 0;
                Integer month = 0;
                Integer day = 0;
                Integer hours = 0;
                Integer minutes = 0;
                Integer seconds = 0;
                Integer nanos = 0;
                Duration duration = null;
                TimeZone timeZone = null;
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    final Object entryValue = entry.getValue();
                    if(entryValue == null) {
                        continue;
                    }
                    if("year".equals(entry.getKey().getName())) {
                        year = (Integer) entryValue;
                    } else if("month".equals(entry.getKey().getName())) {
                        month = (Integer) entryValue;
                    } else if("day".equals(entry.getKey().getName())) {
                        day = (Integer) entryValue;
                    } else if("hours".equals(entry.getKey().getName())) {
                        hours = (Integer) entryValue;
                    } else if("minutes".equals(entry.getKey().getName())) {
                        minutes = (Integer) entryValue;
                    } else if("seconds".equals(entry.getKey().getName())) {
                        seconds = (Integer) entryValue;
                    } else if("nanos".equals(entry.getKey().getName())) {
                        nanos = (Integer) entryValue;
                    } else if("nanos".equals(entry.getKey().getName())) {
                        nanos = (Integer) entryValue;
                    } else if("time_offset".equals(entry.getKey().getName())) {
                        if(entryValue instanceof Duration) {
                            duration = (Duration) entryValue;
                        } else if(entryValue instanceof TimeZone) {
                            timeZone = (TimeZone) entryValue;
                        }
                    }
                }
                final DateTime.Builder builder = DateTime.newBuilder()
                        .setYear(year)
                        .setMonth(month)
                        .setDay(day)
                        .setHours(hours)
                        .setMinutes(minutes)
                        .setSeconds(seconds)
                        .setNanos(nanos);
                if(duration != null) {
                    return builder.setUtcOffset(duration).build();
                } else if(timeZone != null) {
                    return builder.setTimeZone(timeZone).build();
                } else {
                    return builder.build();
                }
            }
            case LATLNG: {
                double latitude = 0D;
                double longitude = 0d;
                for (final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if ("latitude".equals(entry.getKey().getName())) {
                        latitude = (Double) entry.getValue();
                    } else if ("longitude".equals(entry.getKey().getName())) {
                        longitude = (Double) entry.getValue();

                    }
                }
                return LatLng.newBuilder().setLatitude(latitude).setLongitude(longitude).build();
            }
            case ANY: {
                String typeUrl = null;
                ByteString anyValue = null;
                for (final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if ("type_url".equals(entry.getKey().getName())) {
                        typeUrl = entry.getValue().toString();
                    } else if("value".equals(entry.getKey().getName())) {
                        anyValue = (ByteString) entry.getValue();
                    }
                }
                if(typeUrl == null && anyValue == null) {
                    return null;
                }
                return Any.newBuilder().setTypeUrl(typeUrl).setValue(anyValue).build();
            }
            /*
            case DURATION: {
                long seconds = 0L;
                int nanos = 0;
                for (final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if ("seconds".equals(entry.getKey().getName())) {
                        seconds = (Long) entry.getValue();
                    } else if("nanos".equals(entry.getKey().getName())) {
                        nanos = (Integer) entry.getValue();
                    }
                }
                return Duration.newBuilder().setSeconds(seconds).setNanos(nanos).build();
            }
            */
            case BOOL_VALUE: {
                for (final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if ("value".equals(entry.getKey().getName())) {
                        return BoolValue.newBuilder().setValue((Boolean)entry.getValue()).build();
                    }
                }
                return BoolValue.newBuilder().build();
            }
            case STRING_VALUE: {
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if("value".equals(entry.getKey().getName())) {
                        return StringValue.newBuilder().setValue(entry.getValue().toString()).build();
                    }
                }
                return StringValue.newBuilder().build();
            }
            case BYTES_VALUE: {
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if("value".equals(entry.getKey().getName())) {
                        return BytesValue.newBuilder().setValue((ByteString)entry.getValue()).build();
                    }
                }
                return BytesValue.newBuilder().build();

            }
            case INT32_VALUE: {
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if("value".equals(entry.getKey().getName())) {
                        return Int32Value.newBuilder().setValue((Integer)entry.getValue()).build();
                    }
                }
                return Int32Value.newBuilder().build();
            }
            case INT64_VALUE: {
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if("value".equals(entry.getKey().getName())) {
                        return Int64Value.newBuilder().setValue((Long)entry.getValue()).build();
                    }
                }
                return Int64Value.newBuilder().build();
            }
            case UINT32_VALUE: {
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if("value".equals(entry.getKey().getName())) {
                        return UInt32Value.newBuilder().setValue((Integer)entry.getValue()).build();
                    }
                }
                return UInt32Value.newBuilder().build();
            }
            case UINT64_VALUE: {
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if("value".equals(entry.getKey().getName())) {
                        return UInt64Value.newBuilder().setValue((Long)entry.getValue()).build();
                    }
                }
                return UInt64Value.newBuilder().build();
            }
            case FLOAT_VALUE: {
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if("value".equals(entry.getKey().getName())) {
                        return FloatValue.newBuilder().setValue((Float)entry.getValue()).build();
                    }
                }
                return FloatValue.newBuilder().build();
            }
            case DOUBLE_VALUE: {
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if("value".equals(entry.getKey().getName())) {
                        return DoubleValue.newBuilder().setValue((Double)entry.getValue()).build();
                    }
                }
                return DoubleValue.newBuilder().build();
            }
            case TIMESTAMP: {
                Long seconds = 0L;
                Integer nanos = 0;
                for(final Map.Entry<Descriptors.FieldDescriptor, Object> entry : value.getAllFields().entrySet()) {
                    if(entry.getValue() == null) {
                        continue;
                    }
                    if("seconds".equals(entry.getKey().getName())) {
                        if(entry.getValue() instanceof Integer) {
                            seconds = ((Integer) entry.getValue()).longValue();
                        } else {
                            seconds = (Long) entry.getValue();
                        }
                    } else if("nanos".equals(entry.getKey().getName())) {
                        nanos = (Integer) entry.getValue();
                    }
                }
                return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
            }
            case EMPTY: {
                return Empty.newBuilder().build();
            }
            case NULL_VALUE: {
                return NullValue.NULL_VALUE;
            }
            default:
                return value;
        }
    }

    public static Descriptors.FieldDescriptor getFieldDescriptor(final Descriptors.Descriptor descriptor, final String field) {
        return descriptor.getFields().stream()
                .filter(f -> f.getName().equals(field))
                .findAny()
                .orElse(null);
    }

    public static Descriptors.FieldDescriptor getField(final DynamicMessage message, final String field) {
        return message.getDescriptorForType().getFields().stream()
                .filter(e -> e.getName().equals(field))
                .findAny()
                .orElse(null);
    }

    public static Object getFieldValue(final DynamicMessage message, final String field) {
        return message.getAllFields().entrySet().stream()
                .filter(e -> e.getKey().getName().equals(field))
                .map(Map.Entry::getValue)
                .findAny()
                .orElse(null);
    }

    public static long getSecondOfDay(final TimeOfDay time) {
        return LocalTime.of(time.getHours(), time.getMinutes(), time.getSeconds(), time.getNanos()).toSecondOfDay();
    }

    public static long getEpochDay(final Date date) {
        return LocalDate.of(date.getYear(), date.getMonth(), date.getDay()).toEpochDay();
    }

    public static long getEpochMillis(final DateTime dateTime) {
        final LocalDateTime ldt =  LocalDateTime.of(
                dateTime.getYear(), dateTime.getMonth(), dateTime.getDay(),
                dateTime.getHours(), dateTime.getMinutes(), dateTime.getSeconds(), dateTime.getNanos());

        if(dateTime.getTimeZone() == null || dateTime.getTimeZone().getId() == null || dateTime.getTimeZone().getId().trim().length() == 0) {
            return ldt.atOffset(ZoneOffset.UTC).toInstant().toEpochMilli();
        }
        return ldt.atZone(ZoneId.of(dateTime.getTimeZone().getId()))
                .toInstant()
                .toEpochMilli();
    }

    public static boolean hasField(final DynamicMessage message, final String field) {
        return message.getAllFields().entrySet().stream()
                .anyMatch(f -> f.getKey().getName().equals(field));
    }

    private static Map<String, Descriptors.Descriptor> getDescriptors(final DescriptorProtos.FileDescriptorSet set) {
        final List<Descriptors.FileDescriptor> fileDescriptors = new ArrayList<>();
        return getDescriptors(set.getFileList(), fileDescriptors);
    }

    private static Map<String, Descriptors.Descriptor> getDescriptors(
            final List<DescriptorProtos.FileDescriptorProto> files,
            final List<Descriptors.FileDescriptor> fileDescriptors) {

        final int processedSize = fileDescriptors.size();
        final Map<String, Descriptors.Descriptor> descriptors = new HashMap<>();
        final List<DescriptorProtos.FileDescriptorProto> failedFiles = new ArrayList<>();
        for(final DescriptorProtos.FileDescriptorProto file : files) {
            try {
                final Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor
                        .buildFrom(file, fileDescriptors.toArray(new Descriptors.FileDescriptor[fileDescriptors.size()]));
                fileDescriptors.add(fileDescriptor);
                for(final Descriptors.Descriptor messageType : fileDescriptor.getMessageTypes()) {
                    for(final Descriptors.Descriptor childMessageType : messageType.getNestedTypes()) {
                        descriptors.put(childMessageType.getFullName(), childMessageType);
                    }
                    descriptors.put(messageType.getFullName(), messageType);
                }
            } catch (final Descriptors.DescriptorValidationException e) {
                failedFiles.add(file);
            }
        }

        if(processedSize == fileDescriptors.size() && failedFiles.size() > 0) {
            throw new IllegalStateException("Failed to parse descriptors");
        }

        if(failedFiles.size() > 0) {
            descriptors.putAll(getDescriptors(failedFiles, fileDescriptors));
        }

        return descriptors;
    }

}
