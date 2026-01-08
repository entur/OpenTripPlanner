package org.opentripplanner.ext.ojp.mapping;

import de.vdv.ojp20.PersonalModesEnumeration;
import org.opentripplanner.street.search.TraverseMode;

class PersonalModeMapper {

  static PersonalModesEnumeration mapToOjp(TraverseMode mode) {
    return switch (mode) {
      case WALK -> PersonalModesEnumeration.FOOT;
      case BICYCLE -> PersonalModesEnumeration.BICYCLE;
      case SCOOTER -> PersonalModesEnumeration.SCOOTER;
      case CAR, FLEX -> PersonalModesEnumeration.CAR;
    };
  }
}
