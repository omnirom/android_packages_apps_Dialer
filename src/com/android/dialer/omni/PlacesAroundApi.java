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

import java.util.List;


public interface PlacesAroundApi extends PlaceApi {
	
    /**
     * Fetches and returns a list of named Places around the provided latitude and
     * longitude parameters. The bounding box is calculated from lat-distance, lon-distance
     * to lat+distance, lon+distance.
     * This method is NOT asynchronous. Run it in a thread.
     *
     * @param name The name to search
     * @param lat Latitude of the point to search around
     * @param lon Longitude of the point to search around
     * @param distance Max distance (polar coordinates)
     * @return the list of matching places
     */
    List<Place> getNamedPlacesAround(String name, double lat, double lon, double distance);

}
