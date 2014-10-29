/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.model.internal.manage.state;

import com.google.common.collect.ImmutableSortedMap;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.collection.internal.DefaultManagedSet;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

@ThreadSafe
public class ManagedModelElement<T> {

    private final ImmutableSortedMap<String, ModelPropertyInstance<?>> properties;
    private final ModelSchema<T> schema;

    public ManagedModelElement(ModelSchema<T> schema) {
        this.schema = schema;
        ImmutableSortedMap.Builder<String, ModelPropertyInstance<?>> builder = ImmutableSortedMap.naturalOrder();
        for (ModelProperty<?> property : schema.getProperties().values()) {
            builder.put(property.getName(), ModelPropertyInstance.of(property));
        }
        this.properties = builder.build();
    }

    public ModelType<T> getType() {
        return schema.getType();
    }

    public <U> ModelPropertyInstance<U> get(ModelType<U> propertyType, String propertyName) {
        ModelPropertyInstance<?> modelPropertyInstance = properties.get(propertyName);
        ModelType<?> modelPropertyType = modelPropertyInstance.getMeta().getType();
        if (!modelPropertyType.equals(propertyType)) {
            throw new UnexpectedModelPropertyTypeException(propertyName, schema.getType(), propertyType, modelPropertyType);
        }
        return Cast.uncheckedCast(modelPropertyInstance);
    }

    ImmutableSortedMap<String, ModelPropertyInstance<?>> getProperties() {
        return properties;
    }

    public T createInstance() {
        Class<T> concreteType = schema.getType().getConcreteClass();
        if (concreteType.equals(ManagedSet.class)) {
            return Cast.uncheckedCast(createManagedSetInstance(schema.getTypeParameterSchemas().get(0)));
        } else {
            return Cast.uncheckedCast(Proxy.newProxyInstance(concreteType.getClassLoader(), new Class<?>[]{concreteType, ManagedInstance.class}, new ManagedModelElementInvocationHandler()));
        }
    }

    private <P> DefaultManagedSet<P> createManagedSetInstance(final ModelSchema<P> elementSchema) {
        Factory<P> factory = new Factory<P>() {
            public P create() {
                return new ManagedModelElement<P>(elementSchema).createInstance();
            }
        };
        return new DefaultManagedSet<P>(factory);
    }

    private class ManagedModelElementInvocationHandler implements InvocationHandler {

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            String propertyName = StringUtils.uncapitalize(methodName.substring(3));
            if (methodName.startsWith("get")) {
                return getInstanceProperty(ModelType.of(method.getGenericReturnType()), propertyName);
            } else if (methodName.startsWith("set")) {
                setInstanceProperty(ModelType.of(method.getGenericParameterTypes()[0]), propertyName, args[0]);
                return null;
            } else if (methodName.equals("hashCode")) {
                return hashCode();
            }
            throw new Exception("Unexpected method called: " + methodName);
        }

        private <U> void setInstanceProperty(ModelType<U> propertyType, String propertyName, Object value) {
            ModelPropertyInstance<U> modelPropertyInstance = get(propertyType, propertyName);
            if (modelPropertyInstance.getMeta().isManaged() && !ManagedInstance.class.isInstance(value)) {
                throw new IllegalArgumentException(String.format("Only managed model instances can be set as property '%s' of class '%s'", propertyName, schema.getType()));
            }
            modelPropertyInstance.set(Cast.cast(propertyType.getConcreteClass(), value));
        }

        private <U> U getInstanceProperty(ModelType<U> propertyType, String propertyName) {
            return get(propertyType, propertyName).get();
        }
    }
}