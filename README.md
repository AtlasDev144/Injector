# Olympus
A dependency-injection framework inspired by Google's Guice.

[![](https://jitpack.io/v/AtlasDev144/Olympus.svg)](https://jitpack.io/#AtlasDev144/Olympus)

The simplified idea behind this is that you register classes in an `Injector`, essentially telling it that those are dependencies you'll later what access to.
In layman's terms, it's like saying to the Injector "Hey buddy, here's alllll of these things. Hold on to them and just give them to me whenever I ask, anywhere in my code".

## Registration

So the first thing you'll want to do is create an `Injector`. The `Injector` is where all the magic happens.
```java
final Injector injector = new Injector();
```

Now an `Injector` is pointless if we don't actually register any dependencies to be injected, right? To do so, we must use the `Injector.Registrar`.
```java
injector.registrar().register(Test.class);
```

**I should note here that an injectable class, such as Test, _MUST_ have a default constructor, otherwise the Injector won't be able to instantiate it.**
*It can have @Assisted values through, which I discuss below, so long as those @Assisted values have already been registered in the Injector...*

There are multiple ways to register something. What I showed above is the non-preferred way, although it still works. The preferred, and best-practice, way is to use a module. There is an `IInjectorModule` interface in which you can implement, although for convenience, I created an `AbstractInjectorModule` which should serve you well.
```java
public final class CoreModule extends AbstractInjectorModule {
  @Override
  public void enable() {
    this.bind(Test.class);
  
    return this;
  }
}
```
The reason I prefer this method of registration is because it will keep your code neatly organized. You can create as many modules as you want, and even have submodules within modules. As an example, say you have an `EngineModule` and then a module for each 'feature' in your project, keeping your dependencies neatly organized.
```java
public final class DatabaseModule extends AbstractInjectorModule {
  @Override
  public void enable() {
    this.bind(SqlTools.class)
      .bind(SomethingElse.class);
  
    return this;
  }
}
```
```java
public final class ServerModule extends AbstractInjectorModule {
  @Override
  public void enable() {
    this.bind(ServerTools.class);
  
    return this;
  }
}
```
```java
public final class EngineModule extends AbstractInjectorModule {
  @Override
  public void enable() {
    this.bind(new DatabaseModule())
      .bind(new ServerModule());
  
    return this;
  }
}
```
As you can see, we've now embedded two submodules. Also, you can chain `.bind(...)` calls together however you'd like. Now lastly, to register these modules, all we do is:
```java
injector.register(new EngineModule());
```

## Injection

Cool! Now we have a few dependencies registered and ready to be injected, but how exactly do we do that?

First, we need to ensure the classes that we are registering in the `Injector` have the `@Singleton` annotation. This notifies the `Injector` that only one instance of that class will exist for that `Injector`. We do this like so:
```java
@Singleton
public final class Test {
  public final String message = "This is the Test class";
}
```

Now to get that value, we use the `@Inject` annotation on any field we need, then simply tell the `Injector` to give us that value in the constructor of the class needing it.
```java
public final class Example {
  
  @Inject private Test test;
  
  public Example() {
    YourMainClass.INSTANCE.getInjector().inject(this);
    
    System.out.println(this.test.message);
  }
}
```
Get an instance of your `Injector` and call `.inject(this)`. That's it! The `Injector` will now go through all the fields in the class marked as `@Inject`, populating each one with a corresponding value of the same type if it has it. We can now start calling functionality on these fields since they've been injected!

## Assisting

That's really cool, but what if you don't need an injectable class instance itself, but information from it or just need to use it once and forget it? Well we have a handy `@Assisted` annotation for that very thing! Lets say we have a `Zone` class that represents an area on the map and keeps track of all the players within it. We also have a `Zones` class that keeps track of all the `Zone`s themselves. 
```java
public final class Zone {
  private final Set<Player> players;
  
  public boolean has(final Player player) {
    return this.players.stream().anyMatch(p -> p.equals(player));
  }
}
```
```java
@Singleton
public final class Zones {
  private final Set<Zone> zones;
  
  public Optional<Zone> find(final Player player) {
    return this.zones.stream().filter(zone -> zone.has(player)).findFirst();
  }
}
```
Now we register our `@Singleton Zones` class:
```java
public final class WorldModule extends AbstractInjectorModule {
  @Override
  public void enable() {
    this.bind(Zones.class);
    
    return this;
  }
}

...in your class containing your injector...
this.injector().registrar().register(new WorldModule());
```

With the prerequisites out of the way, we have a `PlayerInfo` class that simply contains the `Zone` the player is in. 
```java
public final class PlayerInfo {
  private final Zone zone;
  
  public PlayerInfo(@Assisted Zones zones, Player player) {
    this.zone = zones.find(player).orElse(null);
  }
}
```

That's well and grand, but `@Assisted` constructors must be constructed in a special way. You **cannot** call `new PlayerInfo(player)` and expect the `@Assisted Zones zones` to be automatically given. Instead, to create a new instance of `PlayerInfo`, we use a special method in `Injector`:
```java
final PlayerInfo playerInfo = this.injector().construct(PlayerInfo.class, player);
```
You must give all the non-`@Assisted` arguments for that class' instantiation. If there are none, and **all** the arguments are `@Assisted`, then simply pass nothing like so: `.construct(PlayerInfo.class)`. For the arguments you do give, you're expected to give them in order, otherwise the `Injector` won't be able to instantiate the class.

## Combining Inject and Assisted

Now that you know how to use `@Inject` and `@Assisted`, let's put them together.
```java
public final class Example {
  @Inject private Test test;
  
  private final Zone zone;
  
  public Example(@Assisted Zones zones, final Player player) {
    YourMainClass.INSTANCE.getInjector().inject(this);
    
    this.zone = zones.find(player).orElse(null);
    System.out.println(this.test.message);
  }
}

...somewhere else...
this.injector().construct(Example.class, player);
```
As you can see, even though we are constructing the class via the `Injector`, we still **must** call `YourMainClass.INSTANCE.getInjector().inject(this);` to actually get the `@Inject`ed field populated.

## Edge-case, Non-Singleton Dependencies

Personally, I don't currently see too many use cases for this, however, here it is. You may have asked, "well if every dependency I register in the `Injector` has to be a singleton, what if I want multiple instances of something in the `Injector`?". Enter the `name` value for `@Inject` and `@Assisted`. Say we have the now, non-singleton:
```java
public final class Test {
  private static int count = 0;

  public final String message = "This is the Test class: " + String.valueOf(count++); 
}
```

To register non-singleton classes, which you should **only** be doing if you plan on registering multiple instances, you must give each instance a name:
```java
public final class ExampleModule extends AbstractInjectorModule {
  @Override
  public void enable() {
    this.bind(Test.class, "one")
      .bind(Test.class, "two");
    
    return this;
  }
}
```

Lastly, to inject non-singleton classes, you must specify the name of the instance you want:
```java
public final class Example {
  @Inject(name = "one") Test one;
  @Inject(name = "two") Test two;
  
  public Example() {
    YourMainClass.INSTANCE.getInjector().inject(this);
    
    System.out.println(this.one.message);
    System.out.println(this.two.message);
  }
}
```
The result of the outputs will be 1 digit off from each other, showing that we did in fact inject 2 different instances of the same class. 

This also works for `@Assisted`:
```java
public final class Example {
  public Example(@Assisted(name = "one") Test one, @Assisted(name = "two") Test two) {
    System.out.println(one.message);
    System.out.println(two.message);
  }
}

...somewhere else...
this.injector().construct(Example.class);
```

You can, of course, have as many `@Inject`ed and `@Assisted` values as you want in any given class, and combine them however you'd like. And that about covers everything!
