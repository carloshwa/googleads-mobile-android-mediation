package com.mopub.mobileads.dfp.adapters;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;

import com.google.ads.mediation.mopub.MoPubSingleton;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.MediationBannerAdapter;
import com.google.android.gms.ads.mediation.MediationBannerListener;
import com.google.android.gms.ads.mediation.MediationInterstitialAdapter;
import com.google.android.gms.ads.mediation.MediationInterstitialListener;
import com.google.android.gms.ads.mediation.MediationNativeAdapter;
import com.google.android.gms.ads.mediation.MediationNativeListener;
import com.google.android.gms.ads.mediation.NativeMediationAdRequest;
import com.mopub.common.MoPub;
import com.mopub.common.SdkConfiguration;
import com.mopub.common.SdkInitializationListener;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import static com.google.android.gms.ads.AdRequest.GENDER_FEMALE;
import static com.google.android.gms.ads.AdRequest.GENDER_MALE;

/**
 * A {@link com.mopub.mobileads.dfp.adapters.MoPubAdapter} used to mediate banner ads,
 * interstitial ads and native ads from MoPub.
 */
public class MoPubAdapter implements MediationNativeAdapter, MediationBannerAdapter,
        MediationInterstitialAdapter {
    public static final String TAG = MoPubAdapter.class.getSimpleName();

    private MoPubView mMoPubView;
    private AdSize mAdSize;

    public static final String MOPUB_NATIVE_CEVENT_VERSION = "gmext";
    public static final double DEFAULT_MOPUB_IMAGE_SCALE = 1;
    private static final String MOPUB_AD_UNIT_KEY = "adUnitId";
    private int privacyIconPlacement;
    private int mPrivacyIconSize;

    private static final int MINIMUM_MOPUB_PRIVACY_ICON_SIZE_DP = 10;
    private static final int DEFAULT_MOPUB_PRIVACY_ICON_SIZE_DP = 20;
    private static final int MAXIMUM_MOPUB_PRIVACY_ICON_SIZE_DP = 30;

    @Override
    public void onDestroy() {
        if (mMoPubView != null) {
            mMoPubView.destroy();
            mMoPubView = null;
        }
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void requestNativeAd(final Context context,
                                final MediationNativeListener listener,
                                Bundle serverParameters,
                                NativeMediationAdRequest mediationAdRequest,
                                Bundle mediationExtras) {
        // Stub
    }

    @Override
    public void requestBannerAd(Context context,
                                MediationBannerListener mediationBannerListener,
                                Bundle bundle,
                                AdSize adSize,
                                MediationAdRequest mediationAdRequest,
                                Bundle bundle1) {

        String adunit = bundle.getString(MOPUB_AD_UNIT_KEY);

        mAdSize = getSupportedAdSize(context, adSize);
        if (mAdSize == null) {
            Log.w(TAG, "Failed to request ad, AdSize is null.");
            mediationBannerListener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INVALID_REQUEST);
            return;
        }

        mMoPubView = new MoPubView(context);
        mMoPubView.setBannerAdListener(new MBannerListener(mediationBannerListener));
        mMoPubView.setAdUnitId(adunit);

        //If test mode is enabled
        if (mediationAdRequest.isTesting()) {
            mMoPubView.setTesting(true);
        }

        //If location is available
        if (mediationAdRequest.getLocation() != null) {
            mMoPubView.setLocation(mediationAdRequest.getLocation());
        }

        mMoPubView.setKeywords(getKeywords(mediationAdRequest, false));
        mMoPubView.setUserDataKeywords(getKeywords(mediationAdRequest, true));

        SdkConfiguration sdkConfiguration = new SdkConfiguration.Builder(adunit).build();
        MoPubSingleton.getInstance().initializeMoPubSDK((Activity) context, sdkConfiguration,
                new SdkInitializationListener() {
            @Override
            public void onInitializationFinished() {
               mMoPubView.loadAd();
            }
        });
    }

    private AdSize getSupportedAdSize(Context context, AdSize adSize) {
        AdSize original = new AdSize(adSize.getWidth(),
                adSize.getHeight());

        ArrayList<AdSize> potentials = new ArrayList<>(2);
        potentials.add(AdSize.BANNER);
        potentials.add(AdSize.MEDIUM_RECTANGLE);
        potentials.add(AdSize.LEADERBOARD);
        potentials.add(AdSize.WIDE_SKYSCRAPER);
        Log.i(TAG, potentials.toString());
        return findClosestSize(context, original, potentials);
    }

    // Start of helper code to remove when available in SDK
    /**
     * Find the closest supported AdSize from the list of potentials to the provided size.
     * Returns null if none are within given threshold size range.
     */
    public static AdSize findClosestSize(
            Context context, AdSize original, ArrayList<AdSize> potentials) {
        if (potentials == null || original == null) {
            return null;
        }
        float density = context.getResources().getDisplayMetrics().density;
        int actualWidth = Math.round(original.getWidthInPixels(context)/density);
        int actualHeight = Math.round(original.getHeightInPixels(context)/density);
        original = new AdSize(actualWidth, actualHeight);
        AdSize largestPotential = null;
        for (AdSize potential : potentials) {
            if (isSizeInRange(original, potential)) {
                if (largestPotential == null) {
                    largestPotential = potential;
                } else {
                    largestPotential = getLargerByArea(largestPotential, potential);
                }
            }
        }
        return largestPotential;
    }

    private static boolean isSizeInRange(AdSize original, AdSize potential) {
        if (potential == null) {
            return false;
        }
        double minWidthRatio = 0.5;
        double minHeightRatio = 0.7;

        int originalWidth = original.getWidth();
        int potentialWidth = potential.getWidth();
        int originalHeight = original.getHeight();
        int potentialHeight = potential.getHeight();

        if (originalWidth * minWidthRatio > potentialWidth ||
                originalWidth < potentialWidth) {
            return false;
        }

        if (originalHeight * minHeightRatio > potentialHeight ||
                originalHeight < potentialHeight) {
            return false;
        }
        return true;
    }

    private static AdSize getLargerByArea(AdSize size1, AdSize size2) {
        int area1 = size1.getWidth() * size1.getHeight();
        int area2 = size2.getWidth() * size2.getHeight();
        return area1 > area2 ? size1 : size2;
    }
    // End code to remove when available in SDK

    @Override
    public View getBannerView() {
        return mMoPubView;
    }

    /* Keywords passed from AdMob are separated into 1) personally identifiable, and 2) non-personally
    identifiable categories before they are forwarded to MoPub due to GDPR.
     */
    public static String getKeywords(MediationAdRequest mediationAdRequest, boolean intendedForPII) {

        Date birthday = mediationAdRequest.getBirthday();
        String ageString = "";

        if (birthday != null) {
            int ageInt = getAge(birthday);
            ageString = "m_age:" + Integer.toString(ageInt);
        }

        int gender = mediationAdRequest.getGender();
        String genderString = "";

        if (gender != -1) {
            if (gender == GENDER_FEMALE) {
                genderString = "m_gender:f";
            } else if (gender == GENDER_MALE) {
                genderString = "m_gender:m";
            }
        }

        StringBuilder keywordsBuilder = new StringBuilder();

        keywordsBuilder = keywordsBuilder.append(MOPUB_NATIVE_CEVENT_VERSION)
                .append(",").append(ageString)
                .append(",").append(genderString);

        if (intendedForPII) {
            if (MoPub.canCollectPersonalInformation()) {
                return keywordsContainPII(mediationAdRequest) ? keywordsBuilder.toString() : "";
            } else {
                return "";
            }
        } else {
            return keywordsContainPII(mediationAdRequest) ? "" : keywordsBuilder.toString();
        }
    }

    // Check whether passed keywords contain personally-identifiable information
    private static boolean keywordsContainPII(MediationAdRequest mediationAdRequest) {
        return mediationAdRequest.getBirthday() != null || mediationAdRequest.getGender() !=
                -1 || mediationAdRequest.getLocation() != null;
    }

    private static int getAge(Date birthday) {
        int givenYear = Integer.parseInt((String) DateFormat.format("yyyy", birthday));
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        return currentYear - givenYear;
    }

    private class MBannerListener implements MoPubView.BannerAdListener {
        private MediationBannerListener mMediationBannerListener;

        public MBannerListener(MediationBannerListener bannerListener) {
            mMediationBannerListener = bannerListener;
        }

        @Override
        public void onBannerClicked(MoPubView moPubView) {
            mMediationBannerListener.onAdClicked(MoPubAdapter.this);
            mMediationBannerListener.onAdLeftApplication(MoPubAdapter.this);
        }

        @Override
        public void onBannerCollapsed(MoPubView moPubView) {
            mMediationBannerListener.onAdClosed(MoPubAdapter.this);
        }

        @Override
        public void onBannerExpanded(MoPubView moPubView) {
            mMediationBannerListener.onAdOpened(MoPubAdapter.this);
        }

        @Override
        public void onBannerFailed(MoPubView moPubView,
                                   MoPubErrorCode moPubErrorCode) {
            try {
                switch (moPubErrorCode) {
                    case NO_FILL:
                        mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                                AdRequest.ERROR_CODE_NO_FILL);
                        break;
                    case NETWORK_TIMEOUT:
                        mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                                AdRequest.ERROR_CODE_NETWORK_ERROR);
                        break;
                    case SERVER_ERROR:
                        mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                                AdRequest.ERROR_CODE_INVALID_REQUEST);
                        break;
                    default:
                        mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this,
                                AdRequest.ERROR_CODE_INTERNAL_ERROR);
                        break;
                }
            } catch (NoClassDefFoundError e) {
            }
        }

        @Override
        public void onBannerLoaded(MoPubView moPubView) {
            if (!(mAdSize.getWidth() == moPubView.getAdWidth()
                    && mAdSize.getHeight() == moPubView.getAdHeight())) {
                Log.e(TAG, "The banner ad size loaded does not match the request size. Update the"
                        + " ad size on your MoPub UI to match the request size.");
                mMediationBannerListener.onAdFailedToLoad(MoPubAdapter.this, AdRequest.ERROR_CODE_NO_FILL);
                return;
            }
            mMediationBannerListener.onAdLoaded(MoPubAdapter.this);

        }
    }

    @Override
    public void requestInterstitialAd(Context context,
                                      MediationInterstitialListener mediationInterstitialListener,
                                      Bundle bundle,
                                      MediationAdRequest mediationAdRequest,
                                      Bundle bundle1) {
        // Stub
    }

    @Override
    public void showInterstitial() {
        // Stub
    }

    /**
     * The {@link BundleBuilder} class is used to create a NetworkExtras bundle which can be passed
     * to the adapter to make network-specific customizations.
     */
    public static final class BundleBuilder {

        /**
         * Key to add and obtain {@link #mPrivacyIconSizeDp}.
         */
        private static final String ARG_PRIVACY_ICON_SIZE_DP = "privacy_icon_size_dp";

        /**
         * MoPub's privacy icon size in dp.
         */
        private int mPrivacyIconSizeDp;

        /**
         * Sets the privacy icon size in dp.
         */
        public BundleBuilder setPrivacyIconSize(int iconSizeDp) {
            mPrivacyIconSizeDp = iconSizeDp;
            return BundleBuilder.this;
        }

        /**
         * Constructs a Bundle with the specified extras.
         *
         * @return a {@link Bundle} containing the specified extras.
         */
        public Bundle build() {
            Bundle bundle = new Bundle();
            bundle.putInt(ARG_PRIVACY_ICON_SIZE_DP, mPrivacyIconSizeDp);
            return bundle;
        }
    }
}
