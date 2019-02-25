/**
 * Copyright 2018 Taucoin Core Developers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.taucoin.android.wallet.core;

import java.math.BigInteger;

public class Constants {
    // 1 COIN
    private static final BigInteger COIN = new BigInteger("100", 10);

    // 0.01 COIN
    private static final BigInteger CENT = new BigInteger("1", 10);

    // 0.01 COIN
    public static final BigInteger MIN_CHANGE = CENT;

    // 100 * 100000000 COIN
    public static final BigInteger MAX_MONEY = new BigInteger("10000000000", 10).multiply(COIN);

    //  0.01 COIN
    public static final BigInteger DEFAULT_TX_FEE_MIN = new BigInteger("1", 10);

    // 1 COIN
    public static final BigInteger DEFAULT_TX_FEE_MAX = new BigInteger("66500", 10);
}