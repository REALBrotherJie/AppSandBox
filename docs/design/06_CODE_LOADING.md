# Code Loading Design

## Platform facts

`BaseDexClassLoader` defines a class/resource search path; `DexClassLoader` loads code from specified dex/apk paths; `PathClassLoader` is used for installed application paths. Android's boot class path supplies framework classes. `DexPathList` is an implementation detail and must not be treated as a stable public contract.

## Options

| Option | Benefits | Costs |
|---|---|---|
| A: guest loader parented by host loader | easy host integration | host-class leakage and duplicate-class ambiguity |
| B: guest loader with boot/platform parent only | stronger separation | guest dependencies and framework assumptions are difficult |
| C: dedicated guest loader plus narrow host bridge | explicit policy and diagnostics | bridge design and compatibility work |

`PROPOSED`: option C. The guest loader should delegate framework classes to the boot/platform path, load guest code from the immutable revision, and expose host bridge classes through a small, deliberate API. It must not generally search the host application loader.

## Risks

- Guest and host use the same class name.
- AndroidX/library classes may be duplicated or expect the host context class loader.
- Reflection and `Class.forName` make static analysis incomplete.
- JNI registration uses the defining class loader and can collide.
- Thread context class loader leaks can expose host classes.
- Some Android APIs assume the platform-created `PathClassLoader` shape.

The final hierarchy is `RESEARCH NEEDED` until experiments cover pure Java code, AndroidX dependency, reflection, resource lookup, and JNI registration on API 28, 34, and current devices.

No code loader is implemented in this milestone.
