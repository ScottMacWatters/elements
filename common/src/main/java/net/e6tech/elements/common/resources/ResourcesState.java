/*
 * Copyright 2015 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.common.resources;

import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import net.e6tech.elements.common.logging.TimedLogger;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Created by futeh.
 */
class ResourcesState {

    enum State {
        Initial,
        Open,
        Committed,
        Aborted,
    }

    private InjectionModule module;
    private Injector injector;
    private State state = State.Initial;
    private boolean dirty = false; // dirty if not open and bind is call.
    private List<ResourceProvider> resourceProviders = new LinkedList<>();
    private LinkedList<Object> injectionList = new LinkedList<>();
    private List<Module> modules;
    private boolean useChildInjector = false;

    ResourcesState() {
        module = new InjectionModule();
    }

    protected void cleanup() {
        modules = null;
        module = new InjectionModule();
        resourceProviders.clear();
        state = State.Initial;
        dirty = false;
    }

    void discard() {
        module = null;
        resourceProviders = null;
    }

    public InjectionModule getModule() {
        return module;
    }

    public void setModule(InjectionModule module) {
        this.module = module;
    }

    public Injector getInjector() {
        return injector;
    }

    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public List<ResourceProvider> getResourceProviders() {
        return resourceProviders;
    }

    public void setResourceProviders(List<ResourceProvider> resourceProviders) {
        this.resourceProviders = resourceProviders;
    }

    public LinkedList<Object> getInjectionList() {
        return injectionList;
    }

    public void setInjectionList(LinkedList<Object> injectionList) {
        this.injectionList = injectionList;
    }

    public List<Module> getModules() {
        return modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules;
    }

    public void addModule(InjectionModule module) {
        this.module.add(module);
        setDirty(true);
    }

    protected void createInjector(Resources resources) {
        // this could be recursive so that we really only create injector
        // if is dirty or not yet created.

        if (!useChildInjector && getModules() == null) {
            modules = new ArrayList<>();
            modules.add(getModule());
            if (resources.getResourceManager() != null) {
                modules.add(resources.getResourceManager().getModule().clone());  // clone so that we don't have thread contention
            }
        }

        if (useChildInjector)
            injector = resources.getResourceManager().getInjector().createChildInjector(getModule());
        else
            injector = Guice.createInjector(modules); // injector has to be created first because resourceProviders needs
        // use injection at this point.

        setDirty(false);

        // we need to inject here for objects awaiting to be injected because
        // resourceProviders may depend on these objects.
        while (injectionList.size() > 0) {
            // need to remove item because it may make resources dirty again calling bind or rebind.  In such a case
            // onOpen will be call again.
            Object obj = injectionList.remove();
            _inject(resources, injector, obj);
        }
    }

    public <T> T inject(Resources resources, T object) {
        if (object == null) return object;

        if (state == State.Initial) {
            // to be inject when resources is opened.
            injectionList.add(object);
        } else {
            if (isDirty() || injector == null) {
                createInjector(resources);

                // NOTE an item is added to injectionList during inject but removed during initModule
            }
            _inject(resources, injector, object);

        }
        return object;
    }

    protected void _inject(Resources resources, Injector injector, Object object) {
        if (object instanceof InjectionListener) {
            ((InjectionListener) object).preInject(resources);
        }
        injector.injectMembers(object);
        if (object instanceof InjectionListener) {
            ((InjectionListener) object).injected(resources);
        }
    }

    public <T> T tryBind(Resources resources, Class<T> cls, Callable<T> callable) {
        T o = getModule().getBoundInstance(cls);
        if (o != null) return o;

        if (useChildInjector) {
            o = resources.getResourceManager().getModule().getBoundInstance(cls);
            if (o != null) return o;
        }

        T instance = null;
        try {
            instance = (T) getModule().bindInstance(cls, callable.call());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        setDirty(true);
        return instance;
    }

    public <T> T bind(Resources resources, Class<T> cls, T resource) {
        Object o = getModule().getBoundInstance(cls);
        if (o != null) throw new AlreadyBoundException("Class " + cls + " is already bound to " + o);

        if (useChildInjector) {
            o = resources.getResourceManager().getModule().getBoundInstance(cls);
            if (o != null)
                throw new AlreadyBoundException("Class " + cls + " is already bound in ResourceManager to " + o);
        }

        T instance = (T) getModule().bindInstance(cls, resource);
        setDirty(true);
        return instance;
    }

    public <T> T rebind(Resources resources, Class<T> cls, T resource) {
        T instance = null;

        if (useChildInjector) {
            resources.getResourceManager().getModule().getBoundInstance(cls);
            if (instance != null) throw new AlreadyBoundException("Class " + cls + " is already bound in ResourceManager to " + instance);
        }

        try {
            instance = (T) getModule().bindInstance(cls, resource);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        setDirty(true);
        return instance;
    }

    public <T> T unbind(Class<T> cls) {
        T instance = null;
        try {
            instance = (T) getModule().unbindInstance(cls);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        setDirty(true);
        return instance;
    }

    public void bindClass(Class cls, Class service) {
        Class c = getModule().getBoundClass(cls);
        if (c != null) throw new AlreadyBoundException("Class " + cls + " is already bound to " + c);
        if (service != null) getModule().bindClass(cls, service);
        else getModule().bindInstance(cls, null);
        // if (opened) onOpen();
        setDirty(true);
    }

    public <T> T bindNamedInstance(String name, Class<T> cls, T resource) {
        Object o = getModule().getBoundNamedInstance(name);
        if (o != null) throw new AlreadyBoundException("Class " + cls + " is already bound to " + o);
        return rebindNamedInstance(name, cls, resource);
    }

    public <T> T rebindNamedInstance(String name, Class<T> cls, T resource) {
        T instance = (T) getModule().bindNamedInstance(name, cls, resource);
        setDirty(true);
        return instance;
    }

    public boolean hasInstance(Resources resources, Class cls) {
        if (cls.isAssignableFrom(Resources.class) || cls.isAssignableFrom(ResourceManager.class))
            return true;

        if (getInjector() == null) {
            if (resources.getResourceManager().hasInstance(cls)) return true;
            return getModule().hasInstance(cls);
        } else {
            try {
                getInjector().getInstance(cls);
                return true;
            } catch (ConfigurationException ex) {
                return false;
            }
        }
    }

    public <T> T getInstance(Resources resources, Class<T> cls) throws InstanceNotFoundException {
        if (cls.isAssignableFrom(Resources.class)) {
            return (T) resources;
        } else if (cls.isAssignableFrom(ResourceManager.class)) {
            return (T) resources.getResourceManager();
        }

        if (state == State.Initial || isDirty()) {
            if (getModule().hasInstance(cls)) return (T) getModule().getInstance(cls);
            if (resources.getResourceManager().hasInstance(cls)) return (T) resources.getResourceManager().getInstance(cls);

            // not found
            createInjector(resources);
        }

        try {
            return getInjector().getInstance(cls);
        } catch (ConfigurationException ex) {
            throw new InstanceNotFoundException("No instance for class " + cls.getName() +
                    ". Use newInstance if you meant to create an instance", ex);
        }
    }
}
