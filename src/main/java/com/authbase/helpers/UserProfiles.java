package com.authbase.helpers;

import com.authbase.domain.User;

import java.time.LocalDate;

public final class UserProfiles {

    private UserProfiles() {
    }

    public static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static void set(
            User user,
            String documentNumber,
            String phone,
            LocalDate birthDate,
            String street,
            String city,
            String province,
            String postalCode
    ) {
        user.documentNumber = clean(documentNumber);
        user.phone = clean(phone);
        user.birthDate = birthDate;
        user.street = clean(street);
        user.city = clean(city);
        user.province = clean(province);
        user.postalCode = clean(postalCode);
    }
}
