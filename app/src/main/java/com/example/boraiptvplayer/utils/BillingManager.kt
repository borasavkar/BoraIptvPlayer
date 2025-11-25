package com.example.boraiptvplayer.utils

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*

class BillingManager(
    private val context: Context,
    private val onPurchaseSuccess: () -> Unit, // Satın alma başarılı
    private val onPurchaseCancel: () -> Unit   // Satın alma yok/iptal
) {

    // --- DÜZELTME BURADA: LISTENER EN TEPEYE ALINDI ---
    // Önce dinleyiciyi tanımlıyoruz ki aşağıda kullanabilelim.
    private val purchaseUpdateListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                // Satın alma başarılı (veya deneme süresi aktif)
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    onPurchaseSuccess()
                }
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Kullanıcı iptal etti veya hata oldu
            onPurchaseCancel()
        }
    }

    // Şimdi billingClient'ı oluştururken yukarıdaki 'purchaseUpdateListener'ı tanıyor.
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(purchaseUpdateListener)
        .enablePendingPurchases()
        .build()

    // Senin Play Console'da oluşturacağın Ürün Kimliği (ID)
    // DİKKAT: Buraya Play Console'daki ID'nin aynısını yazmalısın.
    private val PRODUCT_ID = "iptv_monthly_subscription"

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Bağlantı hazır, mevcut abonelikleri kontrol et
                    checkSubscription()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Bağlantı koptu, tekrar dene
                startConnection()
            }
        })
    }

    // Kullanıcının aktif bir aboneliği var mı?
    private fun checkSubscription() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                var isSubscribed = false
                for (purchase in purchases) {
                    // Ürün ID'si eşleşiyor mu ve durumu PURCHASED mı?
                    if (purchase.products.contains(PRODUCT_ID) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        isSubscribed = true
                    }
                }

                if (isSubscribed) {
                    onPurchaseSuccess() // Giriş izni ver
                } else {
                    onPurchaseCancel() // Abone değil
                }
            } else {
                onPurchaseCancel() // Hata durumunda da abone değil varsay
            }
        }
    }

    // Satın Alma Ekranını Aç
    fun launchPurchaseFlow(activity: Activity) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]

                // Abonelik teklifini (Offer Token) bul
                val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .setOfferToken(offerToken)
                                .build()
                        )
                    )
                    .build()

                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }
}