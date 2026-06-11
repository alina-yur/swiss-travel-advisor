package com.example.service;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.data.model.geo.Point;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.sql.Struct;
import java.util.Optional;

@Singleton
public class OraclePointConverter implements TypeConverter<Struct, Point> {

    @Override
    public Optional<Point> convert(Struct object, Class<Point> targetType, ConversionContext context) {
        try {
            Object[] attributes = object.getAttributes();
            if (attributes.length < 3 || !(attributes[2] instanceof Struct sdoPoint)) {
                return Optional.empty();
            }

            Object[] pointAttributes = sdoPoint.getAttributes();
            if (pointAttributes.length < 2 || pointAttributes[0] == null || pointAttributes[1] == null) {
                return Optional.empty();
            }

            return Optional.of(new Point(number(pointAttributes[0]), number(pointAttributes[1])));
        } catch (SQLException | ClassCastException e) {
            return Optional.empty();
        }
    }

    private double number(Object value) {
        return ((Number) value).doubleValue();
    }
}
