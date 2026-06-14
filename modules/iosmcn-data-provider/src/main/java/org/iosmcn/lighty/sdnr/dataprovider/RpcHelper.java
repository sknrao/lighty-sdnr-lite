/*
 * RpcHelper.java
 *
 * Bridges the yangtools Rpc<I, O> interface to a Java method reference,
 * allowing clean registration of RPC implementations with
 * RpcProviderService.registerRpcImplementations().
 *
 * Adapted from ccsdk-features DataProviderServiceImpl.RpcHelper,
 * stripped of OSGi annotations.
 */
package org.iosmcn.lighty.sdnr.dataprovider;

import com.google.common.util.concurrent.ListenableFuture;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.binding.Rpc;
import org.opendaylight.yangtools.binding.RpcInput;
import org.opendaylight.yangtools.binding.RpcOutput;
import org.opendaylight.yangtools.yang.common.RpcResult;

/**
 * Generic adapter that pairs an RPC interface class with a method reference
 * implementing its {@code invoke()} contract.
 *
 * <p>Usage:
 * <pre>
 * rpcProviderService.registerRpcImplementations(List.of(
 *     new RpcHelper&lt;&gt;(ReadStatus.class, this::readStatus),
 *     new RpcHelper&lt;&gt;(ReadFaultcurrentList.class, this::readFaultcurrentList)
 * ));
 * </pre>
 *
 * @param <I> RPC input type
 * @param <O> RPC output type
 */
public final class RpcHelper<I extends RpcInput, O extends RpcOutput> implements Rpc<I, O> {

    /**
     * Functional interface for the actual RPC method implementation.
     */
    @FunctionalInterface
    public interface RpcExecutionWrapper<I extends RpcInput, O extends RpcOutput> {
        ListenableFuture<@NonNull RpcResult<@NonNull O>> execute(@NonNull I input);
    }

    private final Class<? extends Rpc<I, O>> implementedInterface;
    private final RpcExecutionWrapper<I, O> executor;

    public RpcHelper(final Class<? extends Rpc<I, O>> implementedInterface,
                     final RpcExecutionWrapper<I, O> executor) {
        this.implementedInterface = implementedInterface;
        this.executor = executor;
    }

    @Override
    public @NonNull ListenableFuture<@NonNull RpcResult<@NonNull O>> invoke(final @NonNull I input) {
        return this.executor.execute(input);
    }

    @Override
    public @NonNull Class<? extends Rpc<I, O>> implementedInterface() {
        return this.implementedInterface;
    }
}
