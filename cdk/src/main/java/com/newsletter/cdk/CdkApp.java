package com.newsletter.cdk;

import software.amazon.awscdk.App;

public class CdkApp {
    public static void main(String[] args) {
        App app = new App();
        new NewsletterStack(app, "AiCoffeeNewsletterStack");
        app.synth();
    }
}
