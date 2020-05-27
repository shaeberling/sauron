package com.s13g.sauron

import org.simpleframework.http.core.Container

interface ContainerServer {
  fun startServing(container: Container): Boolean
}