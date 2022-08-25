package org.examples.flinkdemo;

public class LightCurve {
  private float lastBrightnessValue;
  private float minFlux;
  private Position position = Position.INGRESS;
  private Integer midTransitCounter = 0;

  public LightCurve(Star star, Planet planet) {
    lastBrightnessValue = 1.0f;
    // Assumes the star's radius is larger than the planet's radius
    minFlux = (planet.getRadius() * planet.getRadius()) / (star.getRadius() * star.getRadius());
  }

  public float curve() {
    switch (position) {
      case INGRESS:
        if (lastBrightnessValue < minFlux) {
          if (midTransitCounter > 30000) {
            position = Position.EGRESS;
            midTransitCounter = 0;
          } else {
            midTransitCounter += 1;
          }
        } else {
          lastBrightnessValue = lastBrightnessValue - 0.0001f;
        };
        break;
      case EGRESS:
        lastBrightnessValue = lastBrightnessValue + 0.0001f;
        if (lastBrightnessValue >= 1.0f) {
          position = Position.COMPLETED;
        }
        break;
    }
    return lastBrightnessValue;
  }

  public Position getPosition() {
    return position;
  }
}
