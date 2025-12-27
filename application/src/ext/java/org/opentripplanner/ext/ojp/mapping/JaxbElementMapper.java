package org.opentripplanner.ext.ojp.mapping;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

class JaxbElementMapper {

  private static final String OJP_NAMESPACE = "http://www.vdv.de/ojp";

  static <T> JAXBElement<T> jaxbElement(T value) {
    var xmlType = value.getClass().getAnnotation(XmlType.class);
    return new JAXBElement<>(
      new QName(OJP_NAMESPACE, extractName(xmlType)),
      (Class<T>) value.getClass(),
      value
    );
  }

  private static String extractName(XmlType xmlType) {
    return xmlType.name().replaceAll("Structure", "");
  }
}
