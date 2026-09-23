# EXP-003B Application Instantiation Plan

## Question

Can `Instrumentation.newApplication(GuestClassLoader, className,
ControlledGuestContext)` create and attach a Guest Application without
calling `onCreate`?

## Hypothesis

The public API will load the class with the Guest loader and attach the
Controlled Context, but will not provide PMS installation or Guest OS identity.

## Tests

Record Application class and defining loader, base Context class and identity,
`application.baseContext`, `application.getPackageName()`,
`getClassLoader()`, `getResources()`, `getAssets()`, and whether
`application.getApplicationContext()` returns the bootstrap context.

Do not call `onCreate`.

## Negative Controls

Direct constructor is run only as Option B control and must be shown to lack a
usable base Context. Host loader cannot load the Guest Application. Application
loader must not equal Host loader.

## Success

Application object is Guest-defined, attached to the intended Context, and no
implicit lifecycle call occurs.

## Failure

If attach or base Context is not observable as intended, stop before C. Do not
reflect into ActivityThread or ContextImpl.

