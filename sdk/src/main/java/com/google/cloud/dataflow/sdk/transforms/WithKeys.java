/*
 * Copyright (C) 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.transforms;

import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.KvCoder;
import com.google.cloud.dataflow.sdk.values.KV;
import com.google.cloud.dataflow.sdk.values.PCollection;
import com.google.common.reflect.TypeToken;

/**
 * {@code WithKeys<K, V>} takes a {@code PCollection<V>}, and either a
 * constant key of type {@code K} or a function from {@code V} to
 * {@code K}, and returns a {@code PCollection<KV<K, V>>}, where each
 * of the values in the input {@code PCollection} has been paired with
 * either the constant key or a key computed from the value.
 *
 * <p> Example of use:
 * <pre> {@code
 * PCollection<String> words = ...;
 * PCollection<KV<Integer, String>> lengthsToWords =
 *     words.apply(WithKeys.of(new SerializableFunction<String, Integer>() {
 *         public Integer apply(String s) { return s.length(); } }));
 * } </pre>
 *
 * <p> Each output element has the same timestamp and is in the same windows
 * as its corresponding input element, and the output {@code PCollection}
 * has the same
 * {@link com.google.cloud.dataflow.sdk.transforms.windowing.WindowingFn}
 * associated with it as the input.
 *
 * @param <K> the type of the keys in the output {@code PCollection}
 * @param <V> the type of the elements in the input
 * {@code PCollection} and the values in the output
 * {@code PCollection}
 */
@SuppressWarnings("serial")
public class WithKeys<K, V> extends PTransform<PCollection<V>,
                                               PCollection<KV<K, V>>> {
  /**
   * Returns a {@code PTransform} that takes a {@code PCollection<V>}
   * and returns a {@code PCollection<KV<K, V>>}, where each of the
   * values in the input {@code PCollection} has been paired with a
   * key computed from the value by invoking the given
   * {@code SerializableFunction}.
   */
  public static <K, V> WithKeys<K, V> of(SerializableFunction<V, K> fn) {
    return new WithKeys<>(fn, null);
  }

  /**
   * Returns a {@code PTransform} that takes a {@code PCollection<V>}
   * and returns a {@code PCollection<KV<K, V>>}, where each of the
   * values in the input {@code PCollection} has been paired with the
   * given key.
   */
  @SuppressWarnings("unchecked")
  public static <K, V> WithKeys<K, V> of(final K key) {
    return new WithKeys<>(
        new SerializableFunction<V, K>() {
          @Override
          public K apply(V value) {
            return key;
          }
        },
        (Class<K>) (key == null ? null : key.getClass()));
  }


  /////////////////////////////////////////////////////////////////////////////

  private SerializableFunction<V, K> fn;
  private transient Class<K> keyClass;

  private WithKeys(SerializableFunction<V, K> fn, Class<K> keyClass) {
    this.fn = fn;
    this.keyClass = keyClass;
  }

  @Override
  public PCollection<KV<K, V>> apply(PCollection<V> in) {
    Coder<K> keyCoder;
    if (keyClass == null) {
      keyCoder = getCoderRegistry().getDefaultOutputCoder(fn, in.getCoder());
    } else {
      keyCoder = getCoderRegistry().getDefaultCoder(TypeToken.of(keyClass));
    }
    PCollection<KV<K, V>> result =
        in.apply(ParDo.named("AddKeys")
                 .of(new DoFn<V, KV<K, V>>() {
                     @Override
                     public void processElement(ProcessContext c) {
                       c.output(KV.of(fn.apply(c.element()),
                                    c.element()));
                     }
                    }));
    if (keyCoder != null) {
      // TODO: Remove when we can set the coder inference context.
      result.setCoder(KvCoder.of(keyCoder, in.getCoder()));
    }
    return result;
  }
}
