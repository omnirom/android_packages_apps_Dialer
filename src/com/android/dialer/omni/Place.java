/*
 *  Copyright (C) 2014 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.dialer.omni;


import android.net.Uri;

import com.google.common.base.Objects;


public class Place {

    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String PHONE_NUMBER = "phone_number";
    public static final String PHONE_TYPE = "phone_type";
    public static final String IS_BUSINESS = "is_business";
    public static final String NAME = "name";
    public static final String STREET = "street";
    public static final String POSTAL_CODE = "postal_code";
    public static final String CITY = "city";
    public static final String EMAIL = "email";
    public static final String WEBSITE = "website";
    public static final String SOURCE = "source";

    public static Place EMPTY = new Place();

    public static boolean isEmpty(Place place) {
        return place == null || place == EMPTY;
    }

    // Latitude of the place
    private double latitude;

    // Longitude of the place
    private double longitude;

    // Phone number of the place
    private String phoneNumber;

    // Type of the phone, e.g. home or mobile
    private int phoneType;

    // Is place business or private
    private boolean isBusiness;

    // Name of the place
    private String name;

    // Street of the place
    private String street;

    // Postal code of the place
    private String postalCode;

    // Name of the city
    private String city;

    // Email address
    private String email;

    // Website
    private String website;

    // Uri of the image
    private Uri imageUri;

    // the provider of this place
    private String source;

    /**
     * @return the latitude
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * @param latitude
     *            the latitude to set
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * @return the longitude
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * @param longitude
     *            the longitude to set
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * @return the phone number
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * @param phoneNumber
     *            the phone number to set
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }


    /**
     * @see com.android.incallui.service.PhoneNumberService.PhoneNumberInfo#getPhoneType
     * @return the phone type
     */
    public int getPhoneType() {
        return phoneType;
    }

    /**
     * @param phoneType
     *            the phone type to set
     */
    public void setPhoneType(int phoneType) {
        this.phoneType = phoneType;
    }

    /**
     * @return true, if the place is business; false, if private
     */
    public boolean isBusiness() {
        return isBusiness;
    }

    /**
     * @param isBusiness
     *            the place type to set
     */
    public void setBusiness(boolean isBusiness) {
        this.isBusiness = isBusiness;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the street
     */
    public String getStreet() {
        return street;
    }

    /**
     * @param street
     *            the street to set
     */
    public void setStreet(String street) {
        this.street = street;
    }

    /**
     * @return the postal code
     */
    public String getPostalCode() {
        return postalCode;
    }

    /**
     * @param postalCode
     *            the postal code to set
     */
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    /**
     * @return the city
     */
    public String getCity() {
        return city;
    }

    /**
     * @param city
     *            the city to set
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * @return the email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * @param email
     *            the email address to set
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * @return the address of the website
     */
    public String getWebsite() {
        return website;
    }

    /**
     * @param website
     *            the website address to set
     */
    public void setWebsite(String website) {
        this.website = website;
    }

    /**
     * @return the image uri
     */
    public Uri getImageUri() {
        return imageUri;
    }

    /**
     * @param imageUri
     *            the uri to set
     */
    public void setImageUri(Uri imageUri) {
        this.imageUri = imageUri;
    }

    /**
     * @see IRemoteApi#getApiProviderName()
     * @return the source of this place
     */
    public String getSource() {
        return source;
    }

    /**
     * @param source
     *            the api provider name
     */
    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        Objects.ToStringHelper toStringHelper = Objects.toStringHelper(this);
        toStringHelper.add("name", name);
        toStringHelper.add("phoneNumber", phoneNumber);
        toStringHelper.add("street", street);
        toStringHelper.add("postalCode", postalCode);
        toStringHelper.add("city", city);
        toStringHelper.add("latitude", latitude);
        toStringHelper.add("longitude", longitude);
        toStringHelper.add("source", source);
        return toStringHelper.toString();
    }
}
